# FastFileLink 4.2.2 / 4.2.3 hook event reference

Derived from the FastFileLink core source at version 4.2.2 (4.2.3 is a rebuild of the same source) (`bases/Kernel.py:46`, `PUBLIC_VERSION`),
2026-10-15. Paths are relative to the FileShare client source tree. This is the wire contract the
`io.fastfilelink.ffl` layer maps; regenerate it when the bundled `ffl.com` changes.

## How forwarding works

- **Every triggered event is forwarded, not a registered subset.** `HookEventForwarder._ensureSubscribed`
  subscribes to the wildcard `FFLEvent.all` (`'*'`) (`bases/Hook.py:728-731`); `EventService.trigger` fires
  `'*'` for every event name (`bases/Kernel.py:331-332`). FFLEvent, UploadEvent, FeatureEvent, `/cli/*` and
  `/source/*` events all reach the hook.
- **Dispatch order.** Specific subscribers run first, then the wildcard (`Kernel.py:331-332`). The forwarder
  is on the wildcard's AFTER signal (`Hook.py:730`).
- **Synchronous.** Forwarding runs on the thread that fired the event (`Hook.py:743-760`, `1137-1144`).
  A slow hook reply blocks the transfer that produced it.
- **Payload clean-up.** Exact-match extractors first (`EXTRACTORS`, `Hook.py:326-350`; upload ones in
  `addons/Upload.py:212-252`), then prefix extractors (`/cli/arguments/` `Hook.py:355-357`, `/feature/class/`
  `addons/Features.py:1503-1505`), then `makeJSONSafe` (`Hook.py:385-437`): dict keys to strings; list/tuple/set
  to list; dataclass to dict; Enum to `.value`; datetime/date to `isoformat()`; Path to str; bytes to UTF-8
  text or base64; anything else to `repr()`.
- **Failures and cooldown.** HTTP 401 permanently disables forwarding (`Hook.py:761-767`). Any other non-200
  or network error counts as a failure; after 3 in a row forwarding pauses for 30 s (`Hook.py:686-687`,
  `780-785`). Endpoint registration is exempt (`Hook.py:755-756`). **A hook that answers 500 three times in a
  row silences every event for 30 s.**
- **File mode.** A `--hook` value that is not `http(s)://` is a JSONL file path, one envelope per line
  (`Hook.py:706`, `504-511`): no replies, no endpoint registration, no sidecar fetch (`Hook.py:1125`, `1138`).

## Envelope

Built at `Hook.py:440-472`, POSTed as JSON (`requests.post(json=...)`), HTTP Basic auth from the URL
userinfo, default timeout 10 s (`Hook.py:610-622`):

```json
{"event": "/upload/progress", "timestamp": "2026-10-15T16:04:05.123456", "data": { ... }}
```

`timestamp` is `datetime.now().isoformat()`: **local time, no timezone offset**, microseconds omitted when 0
(`Hook.py:470`). Timestamps inside `data` vary: `occurredAt` is UTC ISO with `+00:00`; download and preview
`timestamp` is an HTTP-date; p2p `timestamp` is UTC ISO; auth and webrtc-offer `timestamp` is local naive ISO.

Replies: "no" means the body is ignored but the hook must still answer HTTP 200 (`Hook.py:617-620`).
Fields marked `Repr` arrive as Python `repr()` strings.

## share (`bases/Share.py`)

| event | fired at | payload | reply |
|---|---|---|---|
| /share/create | Share.py:512 | uid, name, fileSize:int (0 if unknown), shareMode:'p2p'\|'server', occurredAt | no |
| /share/start | Share.py:521 | uid, occurredAt | no |
| /share/available | Share.py:524 | uid, name, fileSize, shareMode, link, occurredAt | no |
| /share/link/create | Share.py:560 | uid, upload:bool, plus every ShareResult field (Share.py:631-643): file, filePath, contentName, fileSize (-1 if unknown), uploadMode, tunnelType ('default' if none), link, e2ee:bool, pickupCode\|null, pubkeyEnabled, user{serialNumber,name,email,points,level}\|null; manifest only when `reader.supportManifest`. `reader`/`args` removed (Hook.py:274-282) | no |
| /share/complete | Share.py:534 | uid, occurredAt | no |
| /share/stop | Share.py:537 | uid, downloads:int, occurredAt | no |
| /share/fail | Share.py:544 | uid, error:str, occurredAt | no |

## upload (`addons/impl/upload/`, PushUpload only; PullUpload fires none)

| event | fired at | payload | reply |
|---|---|---|---|
| /upload/tell | __init__.py:1161 | uid, filePath (str or list), fileName, fileSize, retention. `reader`/`context` removed (Upload.py:248-252) | **yes**, timeout 10 s (Upload.py:285) |
| /upload/confirm | __init__.py:867 | UploadPointsConfirmationRequest.toDict() (Upload.py:71-78): fileName, fileSize\|null, retention:str, cost:int, currentPoints\|null, remainingPoints\|null, autoConfirm:bool. `result` removed (Upload.py:220-224). Only fired when cost is not None (__init__.py:859) | **yes**, timeout 3600 s (Upload.py:286) |
| /upload/start | Push.py:544 | uploadId, filePath, fileName, fileSize, uploadUrl, strategy ('PushUpload'), e2eeEnabled, resumeMode:bool | no |
| /upload/create | Push.py:635 | uploadId, filePath, fileName, totalChunks, chunkSize, totalSize, e2eeEnabled, strategy | no |
| /upload/progress | Push.py:1566 | uploadId (**null without a resume tracker**), completedChunks, totalChunks, percentage:float, bytesTransferred:int, totalBytes, speed:int B/s, elapsedTime:s, estimatedRemaining:s\|null | no (throttled) |
| /upload/verify | Push.py:749 | uploadId, bytesTransferred, totalBytes, percentage=100.0 | no |
| /upload/pause | Push.py:1605 | uploadId\|null, percentage, completedChunks, totalChunks, bytesTransferred | no |
| /upload/resume | Push.py:350 | uploadId, completedChunks, totalChunks, remainingChunks | no |
| /upload/complete | Push.py:813 | uploadId, link, bytesTransferred, duration, averageSpeed, e2eeEnabled, encryptionKeyCommitment\|null, retention | no |
| /upload/fail | Push.py:674, 700, 711 | uploadId, error, errorType:'ChunkFailure'\|'Timeout'\|'Error', completedChunks, totalChunks, bytesTransferred=0 | no |
| /upload/cancel | Push.py:686 | uploadId, completedChunks, totalChunks, bytesTransferred, canResume=true | no |
| /upload/resume/save | __init__.py:299 | tracker (Repr); internal | no |
| /upload/resume/load | __init__.py:233 | tracker (Repr), context:{}; internal | no |
| /upload/sidecar/fetch | __init__.py:644 | uid. Not an Event: sent with `HookClient.sendEvent` from a background thread, only after a positive /upload/tell reply | **yes**, timeout 300 s |

## download (HTTP handler, `bases/Server.py`)

`timestamp` is the HTTP-date from `date_time_string()`. `connectionType` is the `X-FFL-Transfer-Transport`
header, default 'http': http, http-fallback, webrtc, p2p-tcp, p2p-quic (Settings.py:95-100).

| event | fired at | payload | reply |
|---|---|---|---|
| /download/create | Server.py:622 | timestamp, shareId, downloadId, connectionType, clientInfo{userAgent,domain}, resumeOffset, fileSize, fileName | no |
| /download/progress | Server.py:1087 | shareId, downloadId, bytesTransferred, totalBytes, percentage, speed, connectionType, elapsedTime, estimatedRemaining\|null | no (throttled) |
| /download/complete | Server.py:651 | shareId, downloadId, bytesTransferred, duration, averageSpeed, connectionType, clientInfo | no |
| /download/fail | Server.py:783 | shareId, downloadId, reason:'folder-changed'\|'connection-lost'\|'connection-closed'\|'unknown' (Server.py:732-781), error, bytesTransferred=0, totalBytes=null, duration. No connectionType/clientInfo. **Also fired for WebRTC exceptions** with a random UUID downloadId (Server.py:1498-1513) | no |
| /download/limit/reach | Server.py:1620 | uid, maxDownloads, downloadCount | no |
| /receipt/create | views/DownloadComplete.py:48, 59; Features.py:721, 742 | downloadId (peerId for WebRTC), connectionType:'webrtc'\|'http', clientInfo{userAgent,domain} | no |
| /preview/serve | Server.py:479 | timestamp (HTTP-date), shareId, clientInfo{userAgent,domain}, method; sent for link-preview bots | no |

## webrtc

| event | fired at | payload | reply |
|---|---|---|---|
| /webrtc/offer/create | views/WebRTC.py:70 | peerId, sessionId, timestamp (local naive ISO) | no |
| /webrtc/connect/complete | WebRTC.py:520 | peerId, connectionType:'local'\|'remote', clientInfo{browser,domain,platform (the user agent)} | no |
| /webrtc/transfer/create | WebRTC.py:865 | shareId, peerId, fileName, fileSize, resumeOffset, connectionType='webrtc', e2eeEnabled | no |
| /webrtc/transfer/progress | WebRTC.py:1028 | shareId, peerId, bytesTransferred, totalBytes, percentage, speed, connectionType (no elapsedTime/estimatedRemaining) | no |
| /webrtc/transfer/complete | WebRTC.py:1060 | shareId, peerId, bytesTransferred, duration, averageSpeed, connectionType | no |
| /webrtc/connect, /webrtc/fail, /webrtc/disconnect | Kernel.py:1448, 1453, 1454 | **declared but never fired** | — |

## p2p (`bases/P2P.py`, QUIC)

| event | fired at | payload | reply |
|---|---|---|---|
| /p2p/transfer/create | P2P.py:227 | timestamp (UTC ISO), shareId, downloadId, connectionType='p2p-quic', clientInfo{transport:'ice-udp/quic'}, resumeOffset, fileSize, fileName | no |
| /p2p/transfer/progress | P2P.py:174 | shareId, downloadId, bytesTransferred, totalBytes, percentage, speed, connectionType, elapsedTime, estimatedRemaining | no |
| /p2p/transfer/complete | P2P.py:258 | shareId, downloadId, bytesTransferred, duration, averageSpeed, connectionType, clientInfo | no |
| /p2p/transfer/fail | P2P.py:286 | shareId, downloadId, reason='p2p-quic-failed', error, connectionType | no |

## transfer (integrated, Kernel.py:1465-1496)

| event | built from | payload |
|---|---|---|
| /transfer/create | /download/create, /webrtc/transfer/create, /p2p/transfer/create | source payload plus `downloadId` (copied from peerId if missing) and `clientInfo` (defaults to {}) (Kernel.py:1469-1475) |
| /transfer/progress | the three progress events | same rule |
| /transfer/complete | the three complete events | same rule |
| /transfer/fail | /download/fail, /webrtc/fail, /p2p/transfer/fail | same rule; WebRTC failures arrive through /download/fail |

The hook receives **both** names for each transfer event, and the /transfer/* event arrives **before** its
source event: the relay is an AFTER subscriber of the source (Kernel.py:546-553) and the wildcard runs after
specific subscribers (Kernel.py:331-332). None expects a reply.

## tunnel (`bases/Tunnel.py`)

| event | fired at | payload | reply |
|---|---|---|---|
| /tunnel/start | Tunnel.py:348 | tunnelType ('default' or an external tunnel name), proxyInfo (proxy URL without password, or null) | no |
| /tunnel/create | Tunnel.py:383 | tunnelType, domain, tunnelLink, proxyInfo | no |
| /tunnel/fail | Tunnel.py:366, 393, 403 | tunnelType, error, willRetry=false, retryCount=0. **Usually sent twice per failure** (inner triggers re-raise into the outer except at ~400) | no |
| /tunnel/fallback | Kernel.py:1416 | never fired | — |

## server / session / daemon

| event | fired at | payload | reply |
|---|---|---|---|
| /server/create | Share.py:576 (normal server) | uid, port, domain, then ServerConfig fields (Session.py:323-335) with `authPassword` replaced by `authEnabled:bool` and `defaultWebRTC` by `webrtcEnabled`; also maxDownloads, timeout, authUser, e2eeEnabled, torEnabled, recipientAuth\|null (object, Auth.py:164-172: mode, pickupCode, publicKeyPems[], challenges[], challengeCiphertexts[], recipientEmails[], otpRequestUrl, otpVerifyUrl), receipt, receiptConfirm | no |
| /server/create | Share.py:587 (VFS) | uid, port, domain, maxDownloads=0, timeout=0, authEnabled, e2eeEnabled=false, torEnabled=false, webrtcEnabled=false, link, tunnelType='vfs' | no |
| /server/endpoints/create | Server.py:354 | **never forwarded as itself**; turned into /hook/server/endpoints/register | — |
| /server/shutdown | Server.py:1651 | reason='normal', downloadCount, uptime:s | no |
| /session/create | Server.py:1520 | uid, domain, maxDownloads, timeout, authEnabled, e2eeEnabled, torEnabled, webrtcEnabled | no |
| /session/delete | Server.py:1546 | uid, downloadCount, status:int (ShareStatus 1-6, Share.py:432-438) | no |
| /session/timeout | Server.py:1599 | uid, timeout, downloadCount (Python alias `serverTimeout`, Kernel.py:1431) | no |
| /daemon/endpoints/create | Daemon.py:919 | handler (Repr); fires on every daemon API request | no |
| /daemon/stop | Daemon.py:1151 | (empty); the hook is cleared just before (Daemon.py:1147-1149) | no |

## e2ee

| event | fired at | payload | reply |
|---|---|---|---|
| /e2ee/key/create | Push.py:925 | keyCommitment, mode='upload' | no |
| /e2ee/create | Server.py:1491 | e2eeEnabled=true, mode='p2p', algorithm='AES-256-GCM', chunkSize | no |
| /e2ee/encryption/create | Push.py:939 | fileSize, chunkSize | no |
| /e2ee/decryption/create | Kernel.py:1502 | never fired | — |

## auth (`bases/Auth.py`; `timestamp` is local naive ISO)

| event | fired at | payload | reply |
|---|---|---|---|
| /auth/require | Auth.py:142 | realm, timestamp | no |
| /auth/success | Auth.py:113 | username, timestamp, clientIp | no |
| /auth/fail | Auth.py:124 | username, clientIp, timestamp | no |

## error / application

| event | fired at | payload | notes |
|---|---|---|---|
| /application/start | Kernel.py:1401 | — | never fired |
| /application/shutdown | FFL.py:77 | (empty) | **not forwarded**: `_handleApplicationClosed` clears the forwarder first (Hook.py:736-749) |
| /application/interrupt | FFL.py:338, 348; Share.py:917 | reason='user-interrupt' | **not forwarded**, same reason |
| /error/occur | Kernel.py:1510 | — | never fired (an extractor exists, Hook.py:348) |

## Other forwarded internal events (map as "unknown", ignore)

- /cli/arguments/global/options/get (CLI.py:720): argPolicy{exitCode}.
- /cli/arguments/get (CLI.py:849): argPolicy, proxyConfig, shareSubparser (Repr); `args`/`parser` removed (Hook.py:308-324).
- Other /cli/arguments/*/create (CLI.py:284, 436, 447, 651): fire while the parser is built, normally before `--hook` is configured (CLI.py:701), so usually not delivered.
- /source/reader/create (Readers.py:458): path, fileName, compression, excludeFilter, progressReporter (Repr or null), stdinCache, readerPolicy{reader}.
- /feature/user/resolved (Features.py:333): serialNumber, settingsGetter (Repr), user{serialNumber,name,email,points,level}.
- /feature/receipt/confirm/create (Features.py:1438): receiverMessage, clientInfo.
- /feature/download/issue/create (Features.py:1461): reportId, uid, reason, details, diagnosticRequested, action, source, submittedAt, clientInfo.
- /feature/class/{downloadhandler,webrtcmanager,tunnelrunner,uidgenerator,sharerequest}/create (Features.py:1495-1499): payload is `context` with type objects turned into class names (Hook.py:285-305); AFTER phase only.

## Hook-internal protocol messages (not Event declarations)

| event | sent at | payload | reply |
|---|---|---|---|
| /hook/server/endpoints/register | Hook.py:654-659 (from 853-858) | uid, domain, port, fileName, fileSize, authEnabled, defaultWebRTC, e2eeEnabled, torEnabled, sessionCount (Hook.py:839-850) | **yes**, timeout 5 s |
| /hook/server/request | Hook.py:539, 673-674 | defined, but `dispatchServerRequest` is never called in 4.2.2 | — |
| /upload/sidecar/fetch | upload/__init__.py:644 | uid | **yes** |
| /server/ready | Hook.py:42, 481, 535 | docstring examples only; never sent | — |

## Replies the core reads

The reply must be HTTP 200 and a JSON object, otherwise HookError (Hook.py:643-649). Unless noted, a field
may sit at the top level or under `data`.

- **/upload/confirm**: `confirmed` (bool) at the top level only; sets CONFIRMED or CANCELLED (Upload.py:275-283).
  Without the key the status stays PENDING and the CLI/interactive confirmation runs instead (__init__.py:869-873).
- **/upload/tell**: `sidecar.size`, an int > 0 (__init__.py:702-718). Creates a HookSidecarProducer unless a
  local addon already supplied one (Upload.py:264-273).
- **/upload/sidecar/fetch**: `data` (base64 string) and `manifest` (object); the whole payload may be nested
  under `data` when `data` is an object (__init__.py:650-660).
- **/hook/server/endpoints/register**: `routes` (list, or `data.routes`); missing means empty (Hook.py:659-671).
  Each route is `{method: GET|POST|HEAD, path: "/..." without '?' or '*', encryptResponse?: bool}`
  (Hook.py:812-831). Requests to those routes are proxied to the hook's scheme://host:port plus the route path,
  with Basic auth and `X-FFL-UID/Domain/FileName/FileSize/AuthEnabled/DefaultWebRTC/E2EEEnabled/TorEnabled`
  headers (Hook.py:957-996, 1026-1071). With ffl-java that host:port is its own receiver, so
  `ShareOptions.Builder.hookEndpointProxy(base)` is needed to forward them to the server that really serves
  the routes (status, headers and body are relayed back).
- More reply handlers can be registered with `registerHookResponseHandler` (Hook.py:1099-1113); 4.2.2 has
  only the two upload ones.

## Legacy names

None of `/upload/starting`, `/upload/completed`, `/upload/failed`, `/tunnel/starting`, `/tunnel/created`,
`/server/starting`, `/webrtc/connected`, `/application/interrupted` (nor `/server/ready`, except in docstrings)
appear anywhere in 4.2.2. There is no wire-level alias table. The Python attribute names still carry the old
wording, which gives this mapping for clients written against older cores:

| old name | 4.2.2 name |
|---|---|
| /upload/starting | /upload/start |
| /upload/completed | /upload/complete |
| /upload/failed | /upload/fail |
| /tunnel/starting | /tunnel/start |
| /tunnel/created | /tunnel/create |
| /server/starting | /server/create (Kernel.py:1419, "hook compat") |
| /webrtc/connected | /webrtc/connect/complete |
| /application/interrupted | /application/interrupt (never reaches the hook anyway) |
