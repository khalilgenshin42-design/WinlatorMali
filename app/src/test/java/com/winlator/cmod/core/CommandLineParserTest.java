package com.winlator.cmod.core;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

public class CommandLineParserTest {
    @Test public void preservesWgetCommandTailAsSingleCmdArgument() {
        String tail = "\\\"Z:\\\\opt\\\\wget.exe\\\" -q --show-progress -O Z:\\\\tmp\\\\essentials.exe https://example.invalid/essentials.exe";
        assertArrayEquals(new String[]{"cmd.exe", "/c", tail},
                CommandLineParser.split("cmd.exe /c \\\"Z:\\\\opt\\\\wget.exe -q --show-progress -O Z:\\\\tmp\\\\essentials.exe https://example.invalid/essentials.exe\\\""));
    }

    @Test public void preservesWinePrefixBeforeCmdExe() {
        String tail = "\\\"Z:\\\\opt\\\\wget.exe\\\" -q -O Z:\\\\tmp\\\\x.exe https://example.invalid/x.exe";
        assertArrayEquals(new String[]{"wine", "cmd.exe", "/c", tail},
                CommandLineParser.split("wine cmd.exe /c \\\"Z:\\\\opt\\\\wget.exe -q -O Z:\\\\tmp\\\\x.exe https://example.invalid/x.exe\\\""));
    }

    @Test public void keepsBuiltinCommandTailIntact() {
        assertArrayEquals(new String[]{"cmd.exe", "/c", "echo hello"},
                CommandLineParser.split("cmd.exe /c \\\"echo hello\\\""));
    }

    @Test public void ordinaryQuotedExecutableStillTokenizesNormally() {
        assertArrayEquals(new String[]{"Z:\\\\Program Files\\\\tool.exe", "--quiet"},
                CommandLineParser.split("\\\"Z:\\\\Program Files\\\\tool.exe\\\" --quiet"));
    }
}
