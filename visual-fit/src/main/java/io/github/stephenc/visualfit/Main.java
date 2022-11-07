package io.github.stephenc.visualfit;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.concurrent.Callable;
import picocli.CommandLine;


@CommandLine.Command(name = "visual-fit",
                     mixinStandardHelpOptions = true,
                     description = "Allows manual visual fitting of Gompertz curves to data",
                     subcommands = {
                             IrishData.class,
                             FileData.class,
                             Owid.class,
                             EmptyData.class,
                             OwidTests.class
                     })
public class Main implements Callable<Integer> {

    public static Date parseTimestamp(String v1) {
        return new Date(
                OffsetDateTime.parse(v1.replace('/', '-').replace(' ', 'T').replace("+00", "Z"))
                        .toInstant().toEpochMilli());
    }

    public static void main(String... args) throws IOException {
        int exitCode = new CommandLine(new Main()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
    @Override
    public Integer call() throws Exception {
        CommandLine.usage(this, System.out);
        return 0;
    }
}
