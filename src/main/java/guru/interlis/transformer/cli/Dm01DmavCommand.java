package guru.interlis.transformer.cli;

import picocli.CommandLine.Command;

@Command(
        name = "dm01-dmav",
        description = "DM01 ↔ DMAV product profile utilities",
        mixinStandardHelpOptions = true,
        subcommands = {ImportCorrelationCommand.class})
public final class Dm01DmavCommand implements Runnable {
    @Override
    public void run() {
        new picocli.CommandLine(this).usage(System.out);
    }
}
