"use strict";

const encoder = new TextEncoder();
const decoder = new TextDecoder();
const DATABASE_NAME = "securechat-device";
const STORE_NAME = "keys";
const FILE_LIMIT = 1024 * 1024;
const ALLOWED_FILE_TYPES = new Set([
    "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf", "text/plain"
]);
const OWN_PROFILE_KEY = "securechat-own-profile";
const TRUSTED_PEER_PREFIX = "securechat-trusted-peer:";

let identity = null;
let ownProfile = null;
let peerProfile = null;
let stompClient = null;
let reconnectTimer = null;
const renderedMessageIds = new Set();

const ui = {
    status: document.querySelector("#connection-status"),
    loginScreen: document.querySelector("#login-screen"),
    appPage: document.querySelector("#app-page"),
    signInForm: document.querySelector("#sign-in-form"),
    signInName: document.querySelector("#sign-in-name"),
    signInButton: document.querySelector("#sign-in-button"),
    loginError: document.querySelector("#login-error"),
    myUserId: document.querySelector("#my-user-id"),
    copyId: document.querySelector("#copy-id"),
    displayName: document.querySelector("#display-name"),
    myFingerprint: document.querySelector("#my-fingerprint"),
    peerUserId: document.querySelector("#peer-user-id"),
    loadPeer: document.querySelector("#load-peer"),
    peerCard: document.querySelector("#peer-card"),
    peerSummaryId: document.querySelector("#peer-summary-id"),
    peerFingerprint: document.querySelector("#peer-fingerprint"),
    confirmPeer: document.querySelector("#confirm-peer"),
    chatHeading: document.querySelector("#chat-heading"),
    peerLabel: document.querySelector("#chat-peer-label"),
    conversationNotice: document.querySelector("#conversation-notice"),
    messages: document.querySelector("#messages"),
    textForm: document.querySelector("#text-form"),
    messageText: document.querySelector("#message-text"),
    sendHint: document.querySelector("#send-hint"),
    sendText: document.querySelector("#send-text"),
    fileInput: document.querySelector("#file-input"),
    fileLabel: document.querySelector("#file-label"),
    error: document.querySelector("#app-error")
};

function showError(message) {
    ui.error.textContent = message || "";
}

function setStatus(message, online = false) {
    ui.status.textContent = message;
    ui.status.classList.toggle("online", online);
}

function toBase64Url(value) {
    const bytes = new Uint8Array(value);
    let binary = "";
    for (const byte of bytes) {
        binary += String.fromCharCode(byte);
    }
    return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/u, "");
}

function fromBase64Url(value) {
    const base64 = value.replaceAll("-", "+").replaceAll("_", "/");
    const padded = base64 + "=".repeat((4 - base64.length % 4) % 4);
    const binary = atob(padded);
    return Uint8Array.from(binary, character => character.charCodeAt(0));
}

function openKeyDatabase() {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(DATABASE_NAME, 1);
        request.onupgradeneeded = () => request.result.createObjectStore(STORE_NAME);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

async function readKeys() {
    const database = await openKeyDatabase();
    return new Promise((resolve, reject) => {
        const request = database.transaction(STORE_NAME, "readonly")
            .objectStore(STORE_NAME).get("key-pairs");
        request.onsuccess = () => {
            database.close();
            resolve(request.result || null);
        };
        request.onerror = () => {
            database.close();
            reject(request.error);
        };
    });
}

async function saveKeys(keys) {
    const database = await openKeyDatabase();
    return new Promise((resolve, reject) => {
        const transaction = database.transaction(STORE_NAME, "readwrite");
        transaction.objectStore(STORE_NAME).put(keys, "key-pairs");
        transaction.oncomplete = () => {
            database.close();
            resolve();
        };
        transaction.onerror = () => {
            database.close();
            reject(transaction.error);
        };
    });
}

function createRandomUserId() {
    if (!crypto.randomUUID) {
        throw new Error("Use a current browser with crypto.randomUUID, on localhost or HTTPS.");
    }
    return crypto.randomUUID();
}

async function generateKeys() {
    const encryption = await crypto.subtle.generateKey({
        name: "RSA-OAEP",
        modulusLength: 3072,
        publicExponent: new Uint8Array([1, 0, 1]),
        hash: "SHA-256"
    }, false, ["encrypt", "decrypt"]);
    const signing = await crypto.subtle.generateKey({
        name: "RSA-PSS",
        modulusLength: 3072,
        publicExponent: new Uint8Array([1, 0, 1]),
        hash: "SHA-256"
    }, false, ["sign", "verify"]);
    return { encryption, signing };
}

async function exportPublicKey(publicKey) {
    return toBase64Url(await crypto.subtle.exportKey("spki", publicKey));
}

async function createProfile(userId, keys) {
    return {
        userId,
        encryptionPublicKey: await exportPublicKey(keys.encryption.publicKey),
        signingPublicKey: await exportPublicKey(keys.signing.publicKey)
    };
}

function canonicalProfile(profile) {
    return `securechat-profile-v1\n${profile.userId}\n${profile.encryptionPublicKey}\n${profile.signingPublicKey}`;
}

async function fingerprint(profile) {
    const hash = await crypto.subtle.digest("SHA-256", encoder.encode(canonicalProfile(profile)));
    return [...new Uint8Array(hash)]
        .map(byte => byte.toString(16).padStart(2, "0"))
        .join(":")
        .toUpperCase();
}

async function requestJson(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || `Request failed (${response.status}).`);
    }
    return response.json();
}

async function registerProfile(profile) {
    return requestJson("/api/users", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(profile)
    });
}

async function setupIdentity() {
    if (!crypto?.subtle || !indexedDB) {
        throw new Error("This browser needs Web Crypto and IndexedDB. Open the app at localhost or HTTPS.");
    }
    let keys = await readKeys();
    let userId = localStorage.getItem(OWN_PROFILE_KEY);
    if (!keys || !userId) {
        keys = await generateKeys();
        userId = createRandomUserId();
        await saveKeys(keys);
        localStorage.setItem(OWN_PROFILE_KEY, userId);
    }

    identity = keys;
    ownProfile = await createProfile(userId, keys);
    await registerProfile(ownProfile);
    ui.myUserId.value = userId;
    ui.displayName.value = localStorage.getItem("securechat-display-name") || "";
    ui.myFingerprint.value = await fingerprint(ownProfile);
    ui.copyId.disabled = false;
    setStatus("Connecting…");
    connectSocket();
}

function signedMessageBytes(senderId, receiverId, iv, wrappedKey, encryptedData) {
    return encoder.encode([
        "securechat-message-v1", senderId, receiverId, iv, wrappedKey, encryptedData
    ].join("\n"));
}

function connectionProofBytes(userId, nonce) {
    return encoder.encode(`securechat-connect-v1\n${userId}\n${nonce}`);
}

async function makeWebSocketHeaders() {
    const challenge = await requestJson(`/api/auth/challenge/${encodeURIComponent(ownProfile.userId)}`);
    const signature = await crypto.subtle.sign({
        name: "RSA-PSS",
        saltLength: 32
    }, identity.signing.privateKey, connectionProofBytes(ownProfile.userId, challenge.nonce));
    return {
        "x-user-id": ownProfile.userId,
        "x-nonce": challenge.nonce,
        "x-signature": toBase64Url(signature)
    };
}

function connectSocket() {
    if (!window.Stomp || !window.SockJS) {
        showError("The chat connection library did not load. Check the network or self-host the scripts.");
        return;
    }
    if (stompClient?.connected) {
        stompClient.disconnect(() => {});
    }
    stompClient = Stomp.over(new SockJS("/ws"));
    stompClient.debug = () => {};
    makeWebSocketHeaders().then(headers => {
        stompClient.connect(headers, () => {
            setStatus("Connected", true);
            stompClient.subscribe("/user/queue/messages", frame => {
                receiveMessage(JSON.parse(frame.body)).catch(error => showError(error.message));
            });
            updateControls();
        }, () => {
            setStatus("Reconnecting…");
            updateControls();
            clearTimeout(reconnectTimer);
            reconnectTimer = setTimeout(connectSocket, 3000);
        });
    }).catch(error => {
        setStatus("Could not connect");
        showError(`Connection failed: ${error.message}`);
    });
}

async function fetchPublicProfile(userId) {
    return requestJson(`/api/users/${encodeURIComponent(userId)}/keys`);
}

async function importPublicProfile(profile) {
    return {
        ...profile,
        encryptionKey: await crypto.subtle.importKey(
            "spki", fromBase64Url(profile.encryptionPublicKey),
            { name: "RSA-OAEP", hash: "SHA-256" }, false, ["encrypt"]),
        signingKey: await crypto.subtle.importKey(
            "spki", fromBase64Url(profile.signingPublicKey),
            { name: "RSA-PSS", hash: "SHA-256" }, false, ["verify"])
    };
}

function sameKeys(first, second) {
    return first?.userId === second.userId
        && first.encryptionPublicKey === second.encryptionPublicKey
        && first.signingPublicKey === second.signingPublicKey;
}

function clearPeer() {
    peerProfile = null;
    ui.peerCard.hidden = true;
    ui.peerFingerprint.textContent = "No person selected.";
    ui.confirmPeer.checked = false;
    ui.confirmPeer.disabled = true;
    ui.peerLabel.textContent = "Choose a person to begin";
    ui.conversationNotice.hidden = false;
    ui.messages.replaceChildren();
    updateControls();
}

async function loadPeer() {
    showError("");
    const userId = ui.peerUserId.value.trim();
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(userId)) {
        throw new Error("Enter a valid random UUID.");
    }
    if (userId === ownProfile?.userId) {
        throw new Error("Choose another person's ID.");
    }
    const profile = await fetchPublicProfile(userId);
    const pinnedProfile = JSON.parse(
        localStorage.getItem(`${TRUSTED_PEER_PREFIX}${userId}`) || "null");
    const alreadyPinned = sameKeys(pinnedProfile, profile);
    peerProfile = await importPublicProfile(profile);
    const peerFingerprint = await fingerprint(profile);

    ui.peerCard.hidden = false;
    ui.peerSummaryId.textContent = userId;
    ui.peerFingerprint.textContent = `Fingerprint — compare out-of-band: ${peerFingerprint}`;
    ui.confirmPeer.checked = alreadyPinned;
    ui.confirmPeer.disabled = false;
    ui.peerLabel.textContent = `Secure conversation · ${userId.slice(0, 8)}…`;
    ui.conversationNotice.hidden = true;
    ui.messages.replaceChildren();
    updateControls();
}

function updateControls() {
    const connected = Boolean(stompClient?.connected);
    const ready = Boolean(identity && peerProfile && ui.confirmPeer.checked && connected);
    ui.sendText.disabled = !ready;
    ui.fileInput.disabled = !ready;
    ui.fileLabel.classList.toggle("disabled", !ready);

    if (!connected) {
        ui.sendHint.textContent = "Waiting for a secure connection…";
    } else if (!peerProfile) {
        ui.sendHint.textContent = "To send a message, enter your peer’s UUID and select Find person.";
    } else if (!ui.confirmPeer.checked) {
        ui.sendHint.textContent = "Compare the fingerprint with your peer, then confirm it to enable sending.";
    } else {
        ui.sendHint.textContent = "Peer verified. Your message will be encrypted on this device.";
    }
}

async function getTrustedSenderProfile(senderId) {
    if (senderId === ownProfile.userId) {
        return importPublicProfile(ownProfile);
    }
    const pinnedProfile = JSON.parse(
        localStorage.getItem(`${TRUSTED_PEER_PREFIX}${senderId}`) || "null");
    if (!pinnedProfile) {
        throw new Error("Sender key is not verified on this device. Compare and confirm their fingerprint first.");
    }
    const currentProfile = await fetchPublicProfile(senderId);
    if (!sameKeys(pinnedProfile, currentProfile)) {
        throw new Error("Sender keys changed. Stop and verify the new fingerprint out-of-band.");
    }
    return importPublicProfile(pinnedProfile);
}

class MessagePayload {
    constructor(displayName) {
        if (new.target === MessagePayload) {
            throw new TypeError("MessagePayload is abstract; use a concrete payload type.");
        }
        this.displayName = displayName.slice(0, 40);
        this.messageId = toBase64Url(crypto.getRandomValues(new Uint8Array(16)));
    }

    content() {
        throw new Error("A payload subclass must implement content().");
    }

    async encryptFor(receiver) {
        const iv = crypto.getRandomValues(new Uint8Array(12));
        const aesKey = await crypto.subtle.generateKey(
            { name: "AES-GCM", length: 256 }, true, ["encrypt"]);
        const rawAesKey = await crypto.subtle.exportKey("raw", aesKey);
        const wrappedKey = await crypto.subtle.encrypt(
            { name: "RSA-OAEP" }, receiver.encryptionKey, rawAesKey);
        const ivText = toBase64Url(iv);
        const wrappedKeyText = toBase64Url(wrappedKey);
        const aad = encoder.encode(`securechat-aad-v1\n${ownProfile.userId}\n${receiver.userId}`);
        const plaintext = encoder.encode(JSON.stringify({
            version: 1,
            messageId: this.messageId,
            senderName: this.displayName || "Anonymous",
            ...this.content()
        }));
        const encryptedData = await crypto.subtle.encrypt({
            name: "AES-GCM",
            iv,
            additionalData: aad,
            tagLength: 128
        }, aesKey, plaintext);
        const encryptedDataText = toBase64Url(encryptedData);
        const signature = await crypto.subtle.sign({
            name: "RSA-PSS",
            saltLength: 32
        }, identity.signing.privateKey,
        signedMessageBytes(ownProfile.userId, receiver.userId, ivText, wrappedKeyText, encryptedDataText));

        return {
            senderId: ownProfile.userId,
            receiverId: receiver.userId,
            iv: ivText,
            ciphertext: JSON.stringify({
                wrappedKey: wrappedKeyText,
                encryptedData: encryptedDataText,
                signature: toBase64Url(signature)
            })
        };
    }
}

class TextPayload extends MessagePayload {
    constructor(displayName, text) {
        super(displayName);
        this.text = text;
    }

    content() {
        return { type: "text", text: this.text };
    }
}

class FilePayload extends MessagePayload {
    constructor(displayName, fileName, mimeType, dataUrl) {
        super(displayName);
        this.fileName = fileName.slice(0, 120);
        this.mimeType = mimeType;
        this.dataUrl = dataUrl;
    }

    content() {
        return {
            type: "file",
            fileName: this.fileName,
            mimeType: this.mimeType,
            dataUrl: this.dataUrl
        };
    }
}

async function decryptMessage(packet) {
    if (packet.receiverId !== ownProfile.userId) {
        throw new Error("The message is not addressed to this device.");
    }
    const sender = await getTrustedSenderProfile(packet.senderId);
    const envelope = JSON.parse(packet.ciphertext);
    const signedBytes = signedMessageBytes(
        packet.senderId, packet.receiverId, packet.iv,
        envelope.wrappedKey, envelope.encryptedData);
    const signatureValid = await crypto.subtle.verify({
        name: "RSA-PSS",
        saltLength: 32
    }, sender.signingKey, fromBase64Url(envelope.signature), signedBytes);
    if (!signatureValid) {
        throw new Error("Message signature is invalid. It may be altered or sent by an unverified key.");
    }

    const rawAesKey = await crypto.subtle.decrypt(
        { name: "RSA-OAEP" }, identity.encryption.privateKey, fromBase64Url(envelope.wrappedKey));
    const aesKey = await crypto.subtle.importKey(
        "raw", rawAesKey, { name: "AES-GCM" }, false, ["decrypt"]);
    const plaintextBytes = await crypto.subtle.decrypt({
        name: "AES-GCM",
        iv: fromBase64Url(packet.iv),
        additionalData: encoder.encode(
            `securechat-aad-v1\n${packet.senderId}\n${packet.receiverId}`),
        tagLength: 128
    }, aesKey, fromBase64Url(envelope.encryptedData));
    return {
        senderId: packet.senderId,
        content: JSON.parse(decoder.decode(plaintextBytes))
    };
}

async function receiveMessage(packet) {
    const result = await decryptMessage(packet);
    const messageId = result.content.messageId;
    if (messageId && renderedMessageIds.has(messageId)) {
        return;
    }
    if (messageId) {
        renderedMessageIds.add(messageId);
    }
    renderContent(result.content, result.senderId === ownProfile.userId);
}

function renderContent(content, isMine) {
    const bubble = document.createElement("article");
    bubble.className = isMine ? "message mine" : "message";
    const sender = document.createElement("span");
    sender.className = "sender";
    sender.textContent = isMine ? `You · ${content.senderName}` : content.senderName;
    bubble.append(sender);

    if (content.type === "text" && typeof content.text === "string") {
        const text = document.createElement("span");
        text.textContent = content.text;
        bubble.append(text);
    } else if (content.type === "file" && isSafeFileContent(content)) {
        if (content.mimeType.startsWith("image/")) {
            const image = document.createElement("img");
            image.src = content.dataUrl;
            image.alt = content.fileName;
            bubble.append(image);
        }
        const link = document.createElement("a");
        link.href = content.dataUrl;
        link.download = content.fileName;
        link.textContent = `Download ${content.fileName}`;
        bubble.append(link);
    } else {
        throw new Error("The decrypted message has an unsupported format.");
    }
    ui.messages.append(bubble);
    ui.messages.scrollTop = ui.messages.scrollHeight;
}

function isSafeFileContent(content) {
    return typeof content.fileName === "string"
        && content.fileName.length <= 120
        && ALLOWED_FILE_TYPES.has(content.mimeType)
        && typeof content.dataUrl === "string"
        && content.dataUrl.startsWith(`data:${content.mimeType};base64,`);
}

async function sendPayload(payload) {
    if (!stompClient?.connected || !peerProfile || !ui.confirmPeer.checked) {
        throw new Error("Connect and verify the peer fingerprint before sending.");
    }
    const packet = await payload.encryptFor(peerProfile);
    stompClient.send("/app/chat.send", {}, JSON.stringify(packet));
    renderContent({
        messageId: payload.messageId,
        senderName: payload.displayName || "Anonymous",
        ...payload.content()
    }, true);
}

function readFile(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = () => reject(new Error("The selected file could not be read."));
        reader.readAsDataURL(file);
    });
}

ui.signInName.value = localStorage.getItem("securechat-display-name") || "";

ui.signInForm.addEventListener("submit", async event => {
    event.preventDefault();
    const displayName = ui.signInName.value.trim().slice(0, 40);
    if (!displayName) {
        ui.loginError.textContent = "Enter a name to continue.";
        ui.signInName.focus();
        return;
    }

    ui.loginError.textContent = "";
    ui.signInButton.disabled = true;
    ui.signInButton.querySelector("span").textContent = "Preparing your device…";
    localStorage.setItem("securechat-display-name", displayName);
    try {
        await setupIdentity();
        ui.loginScreen.hidden = true;
        ui.appPage.hidden = false;
    } catch (error) {
        setStatus("Setup failed");
        ui.loginError.textContent = `Could not set up this device: ${error.message}`;
    } finally {
        ui.signInButton.disabled = false;
        ui.signInButton.querySelector("span").textContent = "Continue securely";
    }
});

ui.copyId.addEventListener("click", async () => {
    try {
        await navigator.clipboard.writeText(ui.myUserId.value);
        showError("Your random ID was copied. Share it with your peer.");
    } catch {
        showError("Clipboard access failed. Select and copy your random ID manually.");
    }
});

ui.peerUserId.addEventListener("input", clearPeer);

ui.loadPeer.addEventListener("click", async () => {
    showError("");
    ui.loadPeer.disabled = true;
    try {
        await loadPeer();
    } catch (error) {
        clearPeer();
        showError(`Could not find this person: ${error.message}`);
    } finally {
        ui.loadPeer.disabled = false;
    }
});

ui.confirmPeer.addEventListener("change", () => {
    if (ui.confirmPeer.checked && peerProfile) {
        localStorage.setItem(
            `${TRUSTED_PEER_PREFIX}${peerProfile.userId}`,
            JSON.stringify({
                userId: peerProfile.userId,
                encryptionPublicKey: peerProfile.encryptionPublicKey,
                signingPublicKey: peerProfile.signingPublicKey
            }));
    }
    updateControls();
});

ui.displayName.addEventListener("change", () => {
    localStorage.setItem("securechat-display-name", ui.displayName.value.trim().slice(0, 40));
});

ui.textForm.addEventListener("submit", async event => {
    event.preventDefault();
    showError("");
    const text = ui.messageText.value.trim();
    if (!text) {
        return;
    }
    ui.sendText.disabled = true;
    try {
        await sendPayload(new TextPayload(ui.displayName.value.trim() || "Anonymous", text));
        ui.messageText.value = "";
    } catch (error) {
        showError(`Message was not sent: ${error.message}`);
    } finally {
        updateControls();
    }
});

ui.fileInput.addEventListener("change", async () => {
    const file = ui.fileInput.files[0];
    if (!file) {
        return;
    }
    showError("");
    try {
        if (!ALLOWED_FILE_TYPES.has(file.type) || file.size > FILE_LIMIT) {
            throw new Error("Choose a PDF, text, PNG, JPEG, GIF, or WEBP file up to 1 MiB.");
        }
        await sendPayload(new FilePayload(
            ui.displayName.value.trim() || "Anonymous",
            file.name,
            file.type,
            await readFile(file)));
    } catch (error) {
        showError(`File was not sent: ${error.message}`);
    } finally {
        ui.fileInput.value = "";
        updateControls();
    }
});
