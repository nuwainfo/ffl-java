import io.fastfilelink.ffl.FFL;

public final class PackagingConsumer {
    private PackagingConsumer() {}

    public static void main(String[] args) {
        String version = FFL.version();
        if (!version.contains("FastFileLink")) {
            throw new AssertionError("installed JAR did not execute bundled ffl.com");
        }
        System.out.println(version.lines().findFirst().orElse(version));
    }
}
