package coffee.khyonieheart.eden.rosetta;

import java.io.File;

import coffee.khyonieheart.eden.utils.PrintUtils;
import coffee.khyonieheart.eden.utils.logging.Logger.InfoType;

public class VersionCtrl 
{
    private static String version = null;

    public static String getVersion()
    {
        if (version == null)
        {
            for (String filename : new File("./plugins/").list())
            {
                if (filename.startsWith("Eden-"))
                {
                    PrintUtils.logVerbose("Found Eden plugin, pulling the version from that...", InfoType.INFO);
                    version = "v" + filename.replaceAll("Eden-|.jar", "");
                    return version;
                }
            }

            PrintUtils.log("<Failed to locate the Eden plugin file! Version has been set to \"unknown\". Please ensure that the plugin starts with \"Eden-\".>", InfoType.WARN);
            version = "Unknown";
        }

        return version;
    }
}