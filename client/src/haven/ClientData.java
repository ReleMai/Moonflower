package haven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Resolves Hurricane-owned mutable data outside the rebuildable client package.
 */
public final class ClientData {
    private ClientData() {
    }

    public static Path directory() {
	Path local = Config.localdir();
	Path base = (local == null)
	    ? Utils.path(System.getProperty("user.dir", "."))
	    : Utils.pj(local, "Hurricane");
	try {
	    Files.createDirectories(base);
	} catch(IOException e) {
	    throw(new IllegalStateException("Could not create Hurricane data directory: " + base, e));
	}
	return(base.toAbsolutePath().normalize());
    }

    public static String sqlite(String filename) {
	Path target = directory().resolve(filename);
	migratePackagedDatabase(filename, target);
	return("jdbc:sqlite:" + target);
    }

    private static void migratePackagedDatabase(String filename, Path target) {
	if(Files.exists(target))
	    return;
	Path source = packagedSibling(filename);
	if((source == null) || !Files.isRegularFile(source) || source.equals(target))
	    return;
	try {
	    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
	} catch(java.nio.file.FileAlreadyExistsException e) {
	    /* Another client completed the first-run migration. */
	} catch(IOException e) {
	    new Warning(e, String.format("could not migrate client database %s to %s", source, target))
		.level(Warning.ERROR).issue();
	}
    }

    private static Path packagedSibling(String filename) {
	try {
	    Path location = Utils.srcpath(ClientData.class);
	    Path directory = Files.isDirectory(location) ? location : location.getParent();
	    if(directory != null) {
		Path candidate = directory.resolve(filename).toAbsolutePath().normalize();
		if(Files.isRegularFile(candidate))
		    return(candidate);
	    }
	} catch(RuntimeException ignored) {
	}
	Path working = Utils.path(System.getProperty("user.dir", "."))
	    .resolve(filename).toAbsolutePath().normalize();
	return(Files.isRegularFile(working) ? working : null);
    }
}
