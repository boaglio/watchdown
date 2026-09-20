package com.boaglio.watchdown.cli;

import picocli.CommandLine.IVersionProvider;

/** The version picocli prints for {@code --version} and the renderer records in the provenance. */
public class Version implements IVersionProvider {

    public static String current() {
        String fromManifest = Version.class.getPackage().getImplementationVersion();
        return fromManifest == null || fromManifest.isBlank() ? "dev" : fromManifest;
    }

    @Override
    public String[] getVersion() {
        return new String[] {"watchdown " + current()};
    }
}
