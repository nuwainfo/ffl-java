package io.fastfilelink.ffl;

import apebind.generated.ffl.APEProcessResult;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public final class FFL {
    private static final FFLClient CLIENT = new FFLClient();

    private FFL() {}

    public static String version() {
        return CLIENT.version();
    }

    public static ShareSession share(Path path) {
        return CLIENT.share(path);
    }

    public static ShareSession share(Path path, ShareOptions options) {
        return CLIENT.share(path, options);
    }

    public static ShareSession share(List<Path> paths) {
        return CLIENT.share(paths);
    }

    public static ShareSession share(List<Path> paths, ShareOptions options) {
        return CLIENT.share(paths, options);
    }

    public static ShareSession shareVfs(String url) {
        return CLIENT.shareVfs(url);
    }

    public static ShareSession shareVfs(String url, ShareOptions options) {
        return CLIENT.shareVfs(url, options);
    }

    public static LoginSession login(String email) {
        return CLIENT.login(email);
    }

    public static AuthStatus status() {
        return CLIENT.status();
    }

    public static boolean logout() {
        return CLIENT.logout();
    }

    public static ShareSession shareText(String text) {
        return CLIENT.shareText(text);
    }

    public static ShareSession shareText(String text, String name) {
        return CLIENT.shareText(text, name);
    }

    public static ShareSession shareText(String text, String name, ShareOptions options) {
        return CLIENT.shareText(text, name, options);
    }

    public static ShareSession shareBytes(byte[] data) {
        return CLIENT.shareBytes(data);
    }

    public static ShareSession shareBytes(byte[] data, String name) {
        return CLIENT.shareBytes(data, name);
    }

    public static ShareSession shareBytes(byte[] data, String name, ShareOptions options) {
        return CLIENT.shareBytes(data, name, options);
    }

    public static ShareSession share(InputStream source, String name) {
        return CLIENT.share(source, name);
    }

    public static ShareSession share(
        InputStream source,
        String name,
        ShareOptions options
    ) {
        return CLIENT.share(source, name, options);
    }

    public static DownloadResult download(String url) {
        return CLIENT.download(url);
    }

    public static DownloadResult download(String url, DownloadOptions options) {
        return CLIENT.download(url, options);
    }

    public static DownloadSession startDownload(String url) {
        return CLIENT.startDownload(url);
    }

    public static DownloadSession startDownload(String url, DownloadOptions options) {
        return CLIENT.startDownload(url, options);
    }

    public static DownloadSession downloadStream(String url) {
        return CLIENT.downloadStream(url);
    }

    public static DownloadSession downloadStream(String url, DownloadOptions options) {
        return CLIENT.downloadStream(url, options);
    }

    public static KeygenResult keygen() {
        return CLIENT.keygen();
    }

    public static KeygenResult keygen(String name) {
        return CLIENT.keygen(name);
    }

    public static KeygenResult keygen(String name, KeygenOptions options) {
        return CLIENT.keygen(name, options);
    }

    public static APEProcessResult raw(List<?> arguments) {
        return CLIENT.raw(arguments);
    }

    public static APEProcessResult raw(List<?> arguments, double timeoutSeconds) {
        return CLIENT.raw(arguments, timeoutSeconds);
    }
}
