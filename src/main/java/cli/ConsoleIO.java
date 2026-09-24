package cli;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Objects;

public final class ConsoleIO {
    private final BufferedReader input;
    private final PrintWriter output;
    private final PrintWriter error;

    public ConsoleIO(
            Reader input,
            PrintWriter output,
            PrintWriter error) {

        this.input = new BufferedReader(Objects.requireNonNull(
                input,
                "Input reader must not be null"
        ));
        this.output = Objects.requireNonNull(
                output,
                "Output writer must not be null"
        );
        this.error = Objects.requireNonNull(
                error,
                "Error writer must not be null"
        );
    }

    public String readLine() throws java.io.IOException {
        return input.readLine();
    }

    public void print(String text) {
        output.print(text);
        output.flush();
    }

    public void println(String text) {
        output.println(text);
        output.flush();
    }

    public void error(String text) {
        error.println(text);
        error.flush();
    }
}
