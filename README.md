# SecureChat

SecureChat is an educational Java 17 / Spring Boot 3 chat project. The browser encrypts and signs each message before sending it. The server authenticates a device session, stores the opaque encrypted envelope, and routes it to the recipient's private STOMP queue. It never has a decryption method or a private-key field.

## Project structure

```text
pom.xml
README.md
sql/
  securechat.sql
src/
  main/
    java/com/example/securechat/
      SecureChatApplication.java
      config/
        ChatWebSocketInterceptor.java
        WebSocketConfig.java
      controller/
        ChallengeController.java
        ChatController.java
        UserController.java
      dto/
        ChallengeResponse.java
        EncryptedMessageRequest.java
        RegisterUserRequest.java
        RelayedMessage.java
        UserPublicKeyResponse.java
      entity/
        ChatMessage.java
        User.java
      repository/
        ChatMessageRepository.java
        UserRepository.java
      service/
        ChallengeService.java
        ClientSignatureVerifier.java
        MessageService.java
        MessageServiceImpl.java
        PublicKeyService.java
        UserDirectoryService.java
    resources/
      application.properties
      static/
        chat.js
        index.html
        styles.css
  test/java/com/example/securechat/
    SecureChatApplicationTest.java
    service/
      ChallengeServiceTest.java
      ClientSignatureVerifierTest.java
      PublicKeyServiceTest.java
```

Maven dependencies include Spring Web, Spring WebSocket/STOMP, Spring Data JPA, Bean Validation, MySQL Connector/J, Lombok, Spring Boot Test, and test-only H2. The entities use explicit getters rather than relying on Lombok annotation processing.

## Database configuration and startup

Run [`sql/securechat.sql`](./sql/securechat.sql) in MySQL Workbench or the MySQL command-line client to create the `securechat` database and tables. The SQL uses `BINARY(16)` UUID columns and the same table, column, index, and composite-key names as the JPA entities. For example, from the project root in PowerShell:

```powershell
mysql -u root -p < sql\securechat.sql
```

Create a MySQL application account and use a strong password. Credentials are required via environment variables; they are not committed to the project.

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/securechat?createDatabaseIfNotExist=true&useSSL=true&serverTimezone=UTC"
$env:DB_USERNAME = "your_mysql_user"
$env:DB_PASSWORD = "your_strong_password"
mvn spring-boot:run
```

Open `http://localhost:8080`. The welcome screen asks for a display name, then creates or loads the browser's encryption keys. This is a local sign-in convenience, not a password-based server account: the display name stays in local browser storage and is included only inside encrypted messages. Web Crypto is available on localhost for development; use HTTPS/WSS in deployments.

## Public HTTPS deployment (Railway)

The repository includes a Docker build and Railway configuration. To publish it:

1. Create a Railway project and deploy this GitHub repository (`bharathhngi-ai/securechat`) as a service.
2. Add a Railway MySQL service to the same project.
3. In the app service's variables, add these MySQL service references (replace `MySQL` if you renamed that service):

   ```text
   DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=true&serverTimezone=UTC
   DB_USERNAME=${{MySQL.MYSQLUSER}}
   DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
   ```

4. Deploy the app, then use Railway's **Generate Domain** option to create its public HTTPS address. The app listens on Railway's injected `PORT`; Spring's SockJS endpoint uses the same HTTPS origin.
5. Open the generated HTTPS address in two separate browser profiles to test two devices. Share UUIDs and compare fingerprints over another trusted channel before confirming a peer.

Keep the service at one replica: the current STOMP simple broker and one-time challenge store are in-memory. This is a classroom deployment, not a production messenger; the host can serve modified JavaScript, and it can observe UUIDs, public keys, IP addresses, message direction, timing, and ciphertext sizes. Do not use it for sensitive communications. Review your hosting provider's current pricing and data-retention terms before deploying.

### Stored data

`User` holds a random UUID plus two public keys (RSA-OAEP for encryption and RSA-PSS for signatures) and an enrollment timestamp. It stores no username, email, or private key. Re-registering a UUID with different keys is rejected.

`ChatMessage` stores exactly the five requested data columns: ciphertext, IV, sender UUID, receiver UUID, and timestamp. A composite key of sender, receiver, and timestamp avoids adding a message ID column. Its persistence fields are private and read-only; there is no decryption logic or key data on the server. Hibernate `ddl-auto=update` is suitable only for the classroom starter; use reviewed migrations in production.

## HTTP and STOMP endpoints

| Purpose | Endpoint |
|---|---|
| Register a device UUID and public keys | `POST /api/users` |
| Fetch a recipient public-key profile | `GET /api/users/{userId}/keys` |
| Request a one-time WebSocket challenge | `GET /api/auth/challenge/{userId}` |
| SockJS/STOMP handshake | `/ws` |
| Send encrypted message | `/app/chat.send` |
| Subscribe to your private inbox | `/user/queue/messages` |

The browser signs a fresh, random, short-lived challenge with its RSA-PSS private key before connecting. The inbound WebSocket interceptor checks that proof against the registered public key and sets the UUID as the session `Principal`. It restricts subscriptions to `/user/queue/messages` and sends to `/app/chat.send`. `ChatController` verifies that the request's sender UUID matches the authenticated session, verifies that the recipient exists, stores the opaque packet, and uses `convertAndSendToUser(receiverId, "/queue/messages", packet)`.

The server reads routing fields and required envelope syntax but treats the `ciphertext` string as opaque. It does not parse the inner envelope, inspect plaintext, verify message signatures, or decrypt.

## Client-side encryption and key verification

When a device is initialized, the browser creates:

1. An RSA-OAEP 3072-bit key pair for wrapping per-message AES keys.
2. An RSA-PSS 3072-bit key pair for signing messages and connection challenges.
3. A cryptographically random UUID for the blind routing ID.

Private `CryptoKey`s are generated as non-extractable and stored in browser IndexedDB. Only public keys and the random UUID are registered. The display name is local browser data and is included only inside the encrypted message.

For each text or file message, the browser generates a new AES-256-GCM key and random 96-bit IV, encrypts the JSON payload, wraps the AES key with the recipient's RSA-OAEP public key, and signs sender ID, recipient ID, IV, wrapped key, and ciphertext with RSA-PSS. The wrapped key and signature are sent inside the opaque envelope in the `ciphertext` column. The receiver verifies the sender signature, unwraps the AES key with its private key, then decrypts. Sender and recipient UUIDs are authenticated additional data, binding the encrypted content to its route.

The public-key directory is not a trusted identity service. When a peer UUID is entered, the browser displays the profile's full SHA-256 fingerprint. Compare that fingerprint with the person via a separate trusted channel before confirming it. The browser pins the profile and stops using it if the directory later presents different keys. Both devices should perform this verification.

The UI supports text and PDF/text/PNG/JPEG/GIF/WEBP files up to 1 MiB. Message plaintext and file names remain inside the encrypted payload.

## OOP concepts for Viva

- **Encapsulation:** The backend `ChatMessage` has private fields and no mutators or cryptographic operations; it only represents opaque stored data.
- **Inheritance:** `TextPayload` and `FilePayload` extend the abstract browser-side `MessagePayload`.
- **Polymorphism:** The common `encryptFor()` flow calls `content()` on the base type; the concrete subclass provides the correct payload representation at runtime.
- **Abstraction:** The Java `MessageService` interface defines message persistence behavior; `MessageServiceImpl` implements it with JPA.

## Viva questions

1. **Why can the relay not read messages?**
   AES-GCM encryption happens in the sender's browser. The AES key is wrapped to the recipient's public key, and only the recipient's private key can unwrap it.
2. **Why combine AES and RSA?**
   AES-GCM efficiently encrypts message data and detects tampering. RSA-OAEP protects the small AES key for the recipient.
3. **How do you know the public key belongs to your peer?**
   Compare the full SHA-256 fingerprint over a separate trusted channel; a key returned by the same server alone is not sufficient.
4. **How is the private STOMP queue protected?**
   The client signs a one-time challenge with its registered private signing key. The server authenticates that UUID as the WebSocket principal and only routes to its user destination.
5. **Is the chat anonymous? What metadata remains visible?**
   No. The UUIDs are pseudonymous identifiers. The server can observe IDs, message direction, public keys, IP addresses, timing, and ciphertext sizes.

## Security limitations

This is a learning demo, not a production messenger. A web server that can replace `chat.js` can capture plaintext or operate the browser keys. The project has no forward secrecy, account recovery, group chat, production rate limiting, or anonymity network. Challenge storage and the Spring simple broker are in-memory/single-server only. Do not use for sensitive real-world communications.

Run the Java test suite with `mvn test`. The tests cover application wiring, encrypted-record persistence, RSA key-strength checks, challenge reuse, and client signature verification.
