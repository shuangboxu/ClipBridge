const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();
const keyVersion = "v1";
const keyDerivationName = "PBKDF2-SHA-256";
const keyCipherName = "AES-GCM-256";
const keyIterations = 120000;

export async function encryptTextWithPassword(text, password) {
    // 分享加密完全在浏览器侧完成，服务端只保存密文和解密元数据。
    const normalizedText = String(text ?? "");
    const normalizedPassword = String(password ?? "");
    const salt = randomBytes(16);
    const nonce = randomBytes(12);
    const key = await deriveKey(normalizedPassword, salt);
    const ciphertext = await crypto.subtle.encrypt(
        {
            name: "AES-GCM",
            iv: nonce
        },
        key,
        textEncoder.encode(normalizedText)
    );

    return {
        encryptedPayload: arrayBufferToBase64(ciphertext),
        encryption: buildEncryptionMetadata(salt, nonce)
    };
}

export async function decryptTextWithPassword(encryptedPayload, encryption, password) {
    const key = await deriveKey(String(password ?? ""), base64ToUint8Array(encryption?.salt || ""), Number(encryption?.iterations || 0));
    const plaintextBuffer = await crypto.subtle.decrypt(
        {
            name: "AES-GCM",
            iv: base64ToUint8Array(encryption?.nonce || "")
        },
        key,
        base64ToArrayBuffer(encryptedPayload)
    );
    return textDecoder.decode(plaintextBuffer);
}

export async function encryptFileWithPassword(file, password) {
    if (!(file instanceof File)) {
        throw new Error("请选择要加密的文件。");
    }

    // 文件同样先在浏览器里加密，再把密文文件上传到后端。
    // 这样服务端不需要接触原始文件内容，只负责存储和转发。
    const bytes = await file.arrayBuffer();
    const salt = randomBytes(16);
    const nonce = randomBytes(12);
    const key = await deriveKey(String(password ?? ""), salt);
    const ciphertext = await crypto.subtle.encrypt(
        {
            name: "AES-GCM",
            iv: nonce
        },
        key,
        bytes
    );

    return {
        encryptedFile: new File([ciphertext], "encrypted.bin", {
            type: "application/octet-stream"
        }),
        encryption: buildEncryptionMetadata(salt, nonce)
    };
}

export async function decryptFileWithPassword(blob, encryption, password) {
    const payload = blob instanceof Blob ? await blob.arrayBuffer() : blob;
    const key = await deriveKey(String(password ?? ""), base64ToUint8Array(encryption?.salt || ""), Number(encryption?.iterations || 0));
    return crypto.subtle.decrypt(
        {
            name: "AES-GCM",
            iv: base64ToUint8Array(encryption?.nonce || "")
        },
        key,
        payload
    );
}

function buildEncryptionMetadata(salt, nonce) {
    return {
        version: keyVersion,
        kdf: keyDerivationName,
        iterations: keyIterations,
        salt: uint8ArrayToBase64(salt),
        nonce: uint8ArrayToBase64(nonce),
        cipher: keyCipherName
    };
}

async function deriveKey(password, salt, iterations = keyIterations) {
    if (!globalThis.crypto?.subtle) {
        throw new Error("当前浏览器不支持加密分享。");
    }

    const passwordKey = await crypto.subtle.importKey(
        "raw",
        textEncoder.encode(password),
        "PBKDF2",
        false,
        ["deriveKey"]
    );

    return crypto.subtle.deriveKey(
        {
            name: "PBKDF2",
            hash: "SHA-256",
            salt,
            iterations
        },
        passwordKey,
        {
            name: "AES-GCM",
            length: 256
        },
        false,
        ["encrypt", "decrypt"]
    );
}

function randomBytes(length) {
    const result = new Uint8Array(length);
    crypto.getRandomValues(result);
    return result;
}

function arrayBufferToBase64(value) {
    return uint8ArrayToBase64(new Uint8Array(value));
}

function uint8ArrayToBase64(value) {
    let binary = "";
    const chunkSize = 0x8000;
    for (let index = 0; index < value.length; index += chunkSize) {
        const chunk = value.subarray(index, index + chunkSize);
        binary += String.fromCharCode(...chunk);
    }
    return btoa(binary);
}

function base64ToArrayBuffer(value) {
    return base64ToUint8Array(value).buffer;
}

function base64ToUint8Array(value) {
    const binary = atob(String(value || ""));
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) {
        bytes[index] = binary.charCodeAt(index);
    }
    return bytes;
}
