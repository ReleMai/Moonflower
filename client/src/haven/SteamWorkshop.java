/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.util.*;
import java.io.*;
import java.nio.file.*;
import haven.Steam.UGItem;
import com.codedisaster.steamworks.SteamResult;
import com.codedisaster.steamworks.SteamUGC.ItemUpdateStatus;

public class SteamWorkshop {
    private static final String EXPECTED_APP_ID = "3051280";
    private static final String LEGACY_HURRICANE_WORKSHOP_ID = "3423755273";
    private static final String UPLOAD_CONFIRMATION_PROPERTY = "moonflower.steamUploadConfirmed";
    private static final String DOWNLOAD_CONFIRMATION_PROPERTY = "moonflower.steamDownloadConfirmed";

    private static void err(String format, Object... args) {
	System.err.printf(format + "\n", args);
	System.exit(1);
    }

    private static void usage_upload(PrintStream out) {
	out.println("usage: haven.SteamWorkshop upload [-hq] CLIENT-DIRECTORY [MESSAGE]");
    }

    private static void usage_inspect(PrintStream out) {
	out.println("usage: haven.SteamWorkshop inspect WORKSHOP-ID");
    }

    private static void usage_refresh(PrintStream out) {
	out.println("usage: haven.SteamWorkshop refresh WORKSHOP-ID");
    }

    private static String requiredProperty(Properties props, Path pfile, String name) {
	String value = props.getProperty(name);
	if((value == null) || value.trim().isEmpty())
	    err("upload: %s: lacks %s property", pfile, name);
	return(value == null ? "" : value.trim());
    }

    private static void validatePrivateUpload(Path dir, Path pfile, Properties props) {
	if(!Boolean.getBoolean(UPLOAD_CONFIRMATION_PROPERTY))
	    err("upload: private upload confirmation is missing; use scripts/publish-private-steam-workshop.ps1");

	String name = requiredProperty(props, pfile, "name");
	if(!name.equals("MoonFlower"))
	    err("upload: refusing non-MoonFlower item name: %s", name);

	String visibility = requiredProperty(props, pfile, "visibility");
	if(!visibility.equals("private"))
	    err("upload: MoonFlower publishing is locked to private visibility; found: %s", visibility);

	String description;
	String descriptionFile = props.getProperty("description-file");
	if(descriptionFile != null) {
	    Path path = dir.resolve(descriptionFile.trim());
	    try {
		description = Files.readString(path);
	    } catch(IOException e) {
		err("upload: cannot read description file %s: %s", path, e.getMessage());
		return;
	    }
	} else {
	    description = requiredProperty(props, pfile, "description");
	}
	String normalizedDescription = description.toLowerCase(Locale.ROOT);
	if(normalizedDescription.contains("http://") || normalizedDescription.contains("https://") ||
	   normalizedDescription.contains("github") || normalizedDescription.contains("hurricane") ||
	   normalizedDescription.contains("nightdawg"))
	    err("upload: private MoonFlower description must not expose repositories, URLs, or inherited project identity");

	String preview = requiredProperty(props, pfile, "preview-image");
	if(!Files.isRegularFile(dir.resolve(preview)))
	    err("upload: preview image does not exist: %s", dir.resolve(preview));
	String launcher = requiredProperty(props, pfile, "launcher");
	if(!Files.isRegularFile(dir.resolve(launcher)))
	    err("upload: launcher does not exist: %s", dir.resolve(launcher));
	if(!Files.isRegularFile(dir.resolve("private-publish-manifest.json")))
	    err("upload: audited private-publish-manifest.json is missing");

	String workshopId = props.getProperty("workshop-id");
	if(workshopId != null) {
	    workshopId = workshopId.trim();
	    if(workshopId.equals(LEGACY_HURRICANE_WORKSHOP_ID))
		err("upload: refusing inherited Hurricane workshop-id %s", workshopId);
	    try {
		if(Long.parseLong(workshopId) <= 0)
		    err("upload: workshop-id must be a positive integer: %s", workshopId);
	    } catch(NumberFormatException e) {
		err("upload: invalid workshop-id: %s", workshopId);
	    }
	}

	Path appIdFile = dir.resolve("steam_appid.txt");
	String appId;
	try {
	    appId = Files.readString(appIdFile).trim();
	} catch(IOException e) {
	    err("upload: cannot read required Steam AppID file %s: %s", appIdFile, e.getMessage());
	    return;
	}
	if(!appId.equals(EXPECTED_APP_ID))
	    err("upload: refusing unexpected Steam AppID %s; expected %s", appId, EXPECTED_APP_ID);
    }

    public static void cmd_upload(String[] args) {
	PosixArgs opt = PosixArgs.getopt(args, "hq");
	if(opt == null) {
	    usage_upload(System.err);
	    System.exit(1);
	}
	boolean quiet = false;
	for(char c : opt.parsed()) {
	    switch(c) {
	    case 'h':
		usage_upload(System.out);
		System.exit(0);
		break;
	    case 'q':
		quiet = true;
		break;
	    }
	}
	if(opt.rest.length < 1) {
	    usage_upload(System.err);
	    System.exit(1);
	}
	Path dir = Utils.path(opt.rest[0]);
	if(!Files.exists(dir) || !Files.isDirectory(dir))
	    err("upload: %s: not a directory", dir);
	Path pfile = dir.resolve("workshop-client.properties");
	if(!Files.exists(pfile))
	    err("upload: %s: lacks workshop-client.properties files", dir);
	Properties props = new Properties();
	try(InputStream fp = Files.newInputStream(pfile)) {
	    props.load(fp);
	} catch(IOException e) {
	    e.printStackTrace();
	    System.exit(1);
	}
	validatePrivateUpload(dir, pfile, props);
	Steam api = Steam.get();
	if(api == null)
	    err("upload: could not initialize steam (SteamAppID environment variable missing?)");
	UGItem item;
	if(props.containsKey("workshop-id")) {
	    item = api.ugitem(Long.parseLong(props.getProperty("workshop-id")));
	} else {
	    try {
		item = api.mkugitem();
	    } catch(InterruptedException e) {
		throw(new RuntimeException(e));
	    }
	    System.err.println("upload: note: creating new item");
	    System.err.println("add the following line to workshop-client.properties to update this entry in the future:");
	    System.err.printf("workshop-id=%d\n", item.fid());
	}
	UGItem.Update update = item.new Update();
	String prop;
	update.tags("Client");
	update.contents(dir);
	if((prop = props.getProperty("name")) == null)
	    err("upload: %s: lacks name property\n", pfile);
	update.title(prop);
	if((prop = props.getProperty("description-file")) != null) {
	    try {
		update.description(new String(Files.readAllBytes(dir.resolve(prop)), Utils.utf8));
	    } catch(NoSuchFileException e) {
		err("upload: description file %s: no such file", prop);
	    } catch(IOException e) {
		throw(new RuntimeException(e));
	    }
	} else if((prop = props.getProperty("description")) != null) {
	    update.description(prop);
	} else {
	    err("upload: %s: lacks description or description-file property\n", pfile);
	}
	if((prop = props.getProperty("preview-image")) == null)
	    err("upload: %s: lacks preview-image property\n", pfile);
	Path pvf = dir.resolve(prop);
	if(!Files.exists(pvf))
	    err("upload: preview file %s: no such file", pvf);
	update.preview(pvf);
	update.setprivate();
	update.submit(opt.rest.length > 1 ? opt.rest[1] : null);
	ItemUpdateStatus state = null;
	while(update.done == null) {
	    update.getprogress();
	    if(update.state != state) {
		if(!quiet && update.state != ItemUpdateStatus.Invalid)
		    System.err.println("upload: update state: " + update.state);
		state = update.state;
	    }
	    if((state == ItemUpdateStatus.UploadingContent) || (state == ItemUpdateStatus.UploadingPreviewFile)) {
		if(!quiet && (update.size > 0))
		    System.err.printf("upload: progress: %,d/%,d\n", update.prog, update.size);
	    }
	    try {
		Thread.sleep(100);
	    } catch(InterruptedException e) {
		throw(new RuntimeException(e));
	    }
	}
	if(update.done != SteamResult.OK)
	    err("upload: submission failure: " + update.done);
	if(!update.agreed)
	    System.err.println("upload: note: you need to agree to the Steam Workshop Legal Agreement to make your item public");
    }

    public static void cmd_inspect(String[] args) {
	if(args.length != 1) {
	    usage_inspect(System.err);
	    System.exit(1);
	}
	long id;
	try {
	    id = Long.parseLong(args[0]);
	} catch(NumberFormatException e) {
	    err("inspect: invalid workshop-id: %s", args[0]);
	    return;
	}
	if(id <= 0 || id == Long.parseLong(LEGACY_HURRICANE_WORKSHOP_ID))
	    err("inspect: refusing unexpected workshop-id: %d", id);
	Steam api = Steam.get();
	if(api == null)
	    err("inspect: could not initialize steam (SteamAppID environment variable missing?)");
	UGItem item = api.ugitem(id);
	UGItem.Details details = Loading.waitfor(item.details());
	item.update();
	System.out.printf("workshop-id=%d%n", id);
	System.out.printf("title=%s%n", details.title);
	System.out.printf("time-updated=%d%n", details.timeUpdated);
	System.out.printf("file-size=%d%n", details.fileSize);
	System.out.printf("installed=%s%n", item.installed());
	System.out.printf("needs-update=%s%n", item.stale());
	System.out.printf("downloading=%s%n", item.fetching());
	System.out.printf("download-pending=%s%n", item.pending());
	System.out.printf("install-path=%s%n", item.path());
    }

    public static void cmd_refresh(String[] args) {
	if(!Boolean.getBoolean(DOWNLOAD_CONFIRMATION_PROPERTY))
	    err("refresh: download confirmation is missing");
	if(args.length != 1) {
	    usage_refresh(System.err);
	    System.exit(1);
	}
	long id;
	try {
	    id = Long.parseLong(args[0]);
	} catch(NumberFormatException e) {
	    err("refresh: invalid workshop-id: %s", args[0]);
	    return;
	}
	if(id <= 0 || id == Long.parseLong(LEGACY_HURRICANE_WORKSHOP_ID))
	    err("refresh: refusing unexpected workshop-id: %d", id);
	Steam api = Steam.get();
	if(api == null)
	    err("refresh: could not initialize steam (SteamAppID environment variable missing?)");
	UGItem item = api.ugitem(id);
	item.download(true);
	while(item.dlresult == null) {
	    item.update();
	    if(item.size() > 0)
		System.out.printf("refresh: progress: %,d/%,d%n", item.got(), item.size());
	    try {
		Thread.sleep(250);
	    } catch(InterruptedException e) {
		throw(new RuntimeException(e));
	    }
	}
	if(item.dlresult != SteamResult.OK)
	    err("refresh: download failure: %s", item.dlresult);
	item.update();
	System.out.printf("refresh: completed workshop-id=%d path=%s%n", id, item.path());
    }

    public static void main(String[] args) {
	if(args.length == 0) {
	    System.err.println("usage: haven.SteamWorkshop upload [-hq] CLIENT-DIRECTORY [MESSAGE]");
	    System.err.println("       haven.SteamWorkshop inspect WORKSHOP-ID");
	    System.err.println("       haven.SteamWorkshop refresh WORKSHOP-ID");
	    System.exit(1);
	}
	String cmd = args[0].intern();
	if(cmd == "upload") {
	    cmd_upload(Utils.splice(args, 1));
	} else if(cmd == "inspect") {
	    cmd_inspect(Utils.splice(args, 1));
	} else if(cmd == "refresh") {
	    cmd_refresh(Utils.splice(args, 1));
	} else {
	    System.err.println("invalid workshop command: " + cmd);
	    System.exit(1);
	}
    }
}
