package io.fastfilelink.ffl;

/**
 * Wire names of the hook events FastFileLink 4.2.2 delivers, for {@code ShareSession.onRaw} and
 * {@code ShareOptions.Builder.respondTo}. Payload fields and reply contracts are documented in
 * {@code binding/ffl.hook-events.md}. Events the core declares but never delivers to a hook
 * ({@code /application/*}, {@code /error/occur}, {@code /server/ready}, ...) are left out on purpose.
 */
public final class FFLHookEvents {
    private FFLHookEvents() {}

    // share lifecycle (bases/Share.py): uid, occurredAt, plus name/fileSize/shareMode/link where noted
    public static final String SHARE_CREATE = "/share/create";
    public static final String SHARE_START = "/share/start";
    /** name, fileSize, shareMode, link. */
    public static final String SHARE_AVAILABLE = "/share/available";
    /** upload:bool, link, e2ee, pickupCode, fileSize, contentName, user{...}, manifest (folders). */
    public static final String SHARE_LINK_CREATE = "/share/link/create";
    public static final String SHARE_COMPLETE = "/share/complete";
    /** downloads:int. */
    public static final String SHARE_STOP = "/share/stop";
    /** error. */
    public static final String SHARE_FAIL = "/share/fail";

    // cloud upload (addons/impl/upload): uploadId, counters; three of them expect a reply
    /** Reply: {@code {"sidecar": {"size": N}}} to offer preview data, or {@code {}}. uid, filePath, fileName, fileSize, retention. */
    public static final String UPLOAD_TELL = "/upload/tell";
    /** Reply: {@code {"confirmed": true|false}}; the core waits up to 3600 s. fileName, fileSize, retention, cost, currentPoints, remainingPoints, autoConfirm. */
    public static final String UPLOAD_CONFIRM = "/upload/confirm";
    /** Reply: {@code {"data": "<base64>", "manifest": {...}}}; sent only after a positive tell reply, waits up to 300 s. uid. */
    public static final String UPLOAD_SIDECAR_FETCH = "/upload/sidecar/fetch";
    /** uploadId, filePath, fileName, fileSize, uploadUrl, strategy, e2eeEnabled, resumeMode. */
    public static final String UPLOAD_START = "/upload/start";
    /** uploadId, filePath, fileName, totalChunks, chunkSize, totalSize, e2eeEnabled, strategy. */
    public static final String UPLOAD_CREATE = "/upload/create";
    /** uploadId (null without resume tracker), completedChunks, totalChunks, percentage, bytesTransferred, totalBytes, speed, elapsedTime, estimatedRemaining. */
    public static final String UPLOAD_PROGRESS = "/upload/progress";
    /** uploadId, bytesTransferred, totalBytes, percentage=100. */
    public static final String UPLOAD_VERIFY = "/upload/verify";
    public static final String UPLOAD_PAUSE = "/upload/pause";
    public static final String UPLOAD_RESUME = "/upload/resume";
    /** uploadId, link, bytesTransferred, duration, averageSpeed, e2eeEnabled, encryptionKeyCommitment, retention. */
    public static final String UPLOAD_COMPLETE = "/upload/complete";
    /** uploadId, error, errorType ('ChunkFailure'|'Timeout'|'Error'), completedChunks, totalChunks. */
    public static final String UPLOAD_FAIL = "/upload/fail";
    /** uploadId, completedChunks, totalChunks, bytesTransferred, canResume. */
    public static final String UPLOAD_CANCEL = "/upload/cancel";

    // integrated transfers (bases/Kernel.py): one event per HTTP, WebRTC or P2P transfer, sent
    // before the transport-specific event it was built from; downloadId, clientInfo always present
    public static final String TRANSFER_CREATE = "/transfer/create";
    public static final String TRANSFER_PROGRESS = "/transfer/progress";
    public static final String TRANSFER_COMPLETE = "/transfer/complete";
    public static final String TRANSFER_FAIL = "/transfer/fail";

    // HTTP downloads (bases/Server.py): shareId, downloadId, connectionType, clientInfo
    public static final String DOWNLOAD_CREATE = "/download/create";
    public static final String DOWNLOAD_PROGRESS = "/download/progress";
    public static final String DOWNLOAD_COMPLETE = "/download/complete";
    /** reason ('folder-changed'|'connection-lost'|'connection-closed'|'unknown'), error; also used for WebRTC failures. */
    public static final String DOWNLOAD_FAIL = "/download/fail";
    /** uid, maxDownloads, downloadCount. */
    public static final String DOWNLOAD_LIMIT_REACH = "/download/limit/reach";
    public static final String RECEIPT_CREATE = "/receipt/create";
    public static final String PREVIEW_SERVE = "/preview/serve";

    // WebRTC (views/WebRTC.py): peerId instead of downloadId
    public static final String WEBRTC_OFFER_CREATE = "/webrtc/offer/create";
    public static final String WEBRTC_CONNECT_COMPLETE = "/webrtc/connect/complete";
    public static final String WEBRTC_TRANSFER_CREATE = "/webrtc/transfer/create";
    public static final String WEBRTC_TRANSFER_PROGRESS = "/webrtc/transfer/progress";
    public static final String WEBRTC_TRANSFER_COMPLETE = "/webrtc/transfer/complete";

    // direct P2P over QUIC (bases/P2P.py)
    public static final String P2P_TRANSFER_CREATE = "/p2p/transfer/create";
    public static final String P2P_TRANSFER_PROGRESS = "/p2p/transfer/progress";
    public static final String P2P_TRANSFER_COMPLETE = "/p2p/transfer/complete";
    public static final String P2P_TRANSFER_FAIL = "/p2p/transfer/fail";

    // tunnel (bases/Tunnel.py): tunnelType, proxyInfo
    public static final String TUNNEL_START = "/tunnel/start";
    /** domain, tunnelLink. */
    public static final String TUNNEL_CREATE = "/tunnel/create";
    /** error; usually sent twice per failure. */
    public static final String TUNNEL_FAIL = "/tunnel/fail";

    // local server and sessions (bases/Share.py, bases/Server.py)
    /** uid, port, domain, maxDownloads, timeout, authEnabled, e2eeEnabled, webrtcEnabled, ...; tunnelType='vfs' for VFS shares. */
    public static final String SERVER_CREATE = "/server/create";
    /** reason, downloadCount, uptime. */
    public static final String SERVER_SHUTDOWN = "/server/shutdown";
    public static final String SESSION_CREATE = "/session/create";
    public static final String SESSION_DELETE = "/session/delete";
    /** uid, timeout, downloadCount. */
    public static final String SESSION_TIMEOUT = "/session/timeout";

    // end-to-end encryption
    public static final String E2EE_KEY_CREATE = "/e2ee/key/create";
    public static final String E2EE_CREATE = "/e2ee/create";
    public static final String E2EE_ENCRYPTION_CREATE = "/e2ee/encryption/create";

    // HTTP basic auth on the share (bases/Auth.py)
    public static final String AUTH_REQUIRE = "/auth/require";
    public static final String AUTH_SUCCESS = "/auth/success";
    public static final String AUTH_FAIL = "/auth/fail";

    // hook protocol (bases/Hook.py)
    /** Reply: {@code {"routes": [{"method", "path", "encryptResponse"?}]}} within 5 s; uid, domain, port, fileName, fileSize, e2eeEnabled, ... */
    public static final String ENDPOINTS_REGISTER = "/hook/server/endpoints/register";
}
