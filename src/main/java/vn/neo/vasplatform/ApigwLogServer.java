package vn.neo.vasplatform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.io.FilenameFilter;

@EnableScheduling
@Configuration
public class ApigwLogServer {

    private static Logger logger = LoggerFactory.getLogger(ApigwLogServer.class);

    private static String[] getListFile(String path) {

        File file = new File(path);
        File[] files = file.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                if (name.toLowerCase().startsWith("spring")) {
                    return true;
                } else {
                    return false;
                }
            }
        });
        String[] result = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            result[i] = path + "/" + files[i].getName();
        }
        return result;
    }

    public static void main(String[] args) {
        String[] list = getListFile("config");
        @SuppressWarnings("resource")
        ApplicationContext context = new FileSystemXmlApplicationContext(list);
        logger.debug("ApigwLogServer started");
        System.out.println("ApigwLogServer started");
    }
}
