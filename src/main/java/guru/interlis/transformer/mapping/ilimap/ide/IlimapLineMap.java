package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class IlimapLineMap {

    private final String text;
    private final int[] lineStarts;

    public IlimapLineMap(String text) {
        this.text = Objects.requireNonNull(text, "text");
        this.lineStarts = buildLineStarts(text);
    }

    public int lineCount() {
        return lineStarts.length;
    }

    public int offsetToZeroBasedLine(int offset) {
        int clampedOffset = clampOffset(offset);
        int line = Arrays.binarySearch(lineStarts, clampedOffset);
        return line >= 0 ? line : -line - 2;
    }

    public int offsetToZeroBasedCharacter(int offset) {
        int clampedOffset = clampOffset(offset);
        int line = offsetToZeroBasedLine(clampedOffset);
        return clampedOffset - lineStarts[line];
    }

    public int positionToOffset(int zeroBasedLine, int zeroBasedCharacter) {
        int line = clampLine(zeroBasedLine);
        int character = Math.max(0, Math.min(zeroBasedCharacter, lineLength(line)));
        return lineStarts[line] + character;
    }

    public IlimapIdePosition toIdePosition(int offset) {
        int clampedOffset = clampOffset(offset);
        return new IlimapIdePosition(offsetToZeroBasedLine(clampedOffset), offsetToZeroBasedCharacter(clampedOffset));
    }

    public IlimapIdeRange toIdeRange(IlimapSourceRange sourceRange) {
        Objects.requireNonNull(sourceRange, "sourceRange");
        return new IlimapIdeRange(
                toIdePosition(sourceRange.start().offset()),
                toIdePosition(sourceRange.end().offset()));
    }

    private int clampOffset(int offset) {
        return Math.max(0, Math.min(offset, text.length()));
    }

    private int clampLine(int line) {
        return Math.max(0, Math.min(line, lineStarts.length - 1));
    }

    private int lineLength(int zeroBasedLine) {
        int start = lineStarts[zeroBasedLine];
        if (zeroBasedLine == lineStarts.length - 1) {
            return text.length() - start;
        }
        int nextStart = lineStarts[zeroBasedLine + 1];
        return nextStart - start - 1;
    }

    private static int[] buildLineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }
}
