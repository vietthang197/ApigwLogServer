package vn.neo.vasplatform.jobs;

import com.neo.jdbc.ConnectDatabase;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.neo.vasplatform.utils.Constants;
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Calendar;

@Component
@EnableScheduling
public class SchedulerTask {

    private Logger logger = LoggerFactory.getLogger(SchedulerTask.class);

    @Autowired
    @Qualifier("propertiesConfiguration")
    private PropertiesConfiguration props;

    @Autowired
    @Qualifier("connectDatabase")
    private ConnectDatabase connectDatabase;

    @Scheduled(cron = "10 * * ? * *")
    public void cleanMemory() {
        System.gc();
    }

    @Scheduled(cron = "10 * * ? * *")
    public void doWriteLogToDb() throws SQLException, IOException {
        String dirShareLogMnt = props.getString(Constants.DIR_SHARE_LOG_MNT);
        String logMtVpFileName = props.getString(Constants.LOG_MTVP_FILE_NAME);
        String dirLogAppend = props.getString(Constants.DIR_LOG_APPEND);
        String db = props.getString(Constants.DB_NAME);

        StringBuilder dirFileLogMtVpEnd = new StringBuilder();
        dirFileLogMtVpEnd.append(dirLogAppend);
        dirFileLogMtVpEnd.append("/");
        Calendar calendar = Calendar.getInstance();
        dirFileLogMtVpEnd.append(logMtVpFileName);
        dirFileLogMtVpEnd.append("-");
        dirFileLogMtVpEnd.append(calendar.get(Calendar.YEAR));
        dirFileLogMtVpEnd.append("-");
        dirFileLogMtVpEnd.append(calendar.get(Calendar.MONTH) + 1);
        dirFileLogMtVpEnd.append("-");
        dirFileLogMtVpEnd.append(calendar.get(Calendar.DATE));
        dirFileLogMtVpEnd.append(".log");

        logger.debug("************RUNNING SCHEDULER WRITE LOG************");
        long startTime = System.nanoTime();
        logger.debug("DO TASK WRITE LOG TO DATABASE");

        File resource = new File(dirShareLogMnt);

        if (!resource.exists())
            logger.debug("RESOURCE SHARE LOG NOT FOUND");

        File[] files = resource.listFiles();
        logger.debug("FOUND {} FILES IN FOLDER SHARED LOG: {}", files == null ? 0 : files.length, dirShareLogMnt);

        File logAppend = new File(dirFileLogMtVpEnd.toString());
        logger.debug("FILE LOG APPEND TO DAY: {}", dirFileLogMtVpEnd.toString());

        if (!logAppend.exists())
            logAppend.createNewFile();

        if (!logAppend.exists()) {
            logger.debug("NOT FOUND APPEND FILE");
            return;
        }

        Connection connection = null;
        PreparedStatement ps = null;
        BufferedWriter writer = null;
        int errorItem = 0;
        try {

            if (files != null) {

                try {
                    connection = connectDatabase.getConnection(db);
                    connection.setAutoCommit(false);
                    String sqlInsertLog1 = props.getString(Constants.ORACLE_SQL1);

                    if (sqlInsertLog1 == null) {
                        logger.debug("SQL_INSERT_LOG1 IS NULL");
                        return;
                    }

                    ps = connection.prepareStatement(sqlInsertLog1);
                } catch (Exception e) {
                    logger.debug("ERROR INIT CONNECTION POOL!");
                }

                writer = new BufferedWriter(new FileWriter(logAppend, true));

                for (File fileItem : files) {
                    errorItem++;
                    BufferedReader reader = null;
                    try {
                        reader = new BufferedReader(new FileReader(fileItem));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            writer.append(line + "\n");

                            String[] lineColumn = line.split("\\t");

                            ps.setString(1, lineColumn[0]);
                            ps.setString(2, lineColumn[1]);
                            ps.setString(3, lineColumn[2]);
                            ps.setString(4, lineColumn[3]);
                            ps.setInt(5, Integer.parseInt(lineColumn[4]));
                            ps.setString(6, lineColumn[5]);
                            ps.setDate(7, new java.sql.Date(System.currentTimeMillis()));
                            ps.setString(8, lineColumn[6]);

                            ps.addBatch();
                        }

                        ps.executeBatch();

                        reader.close();

                        if (fileItem.delete())
                            logger.debug("FILE LOG TEMP DELETE SUCCESSFULLY!: {}", fileItem.getName());
                        else {
                            logger.debug("FILE DELETE FAILED!");
                            logger.debug("ROLLBACK!");
                            connection.rollback();
                        }

                        connection.commit();
                    } catch (Exception e1) {
                        if (connection != null)
                            connection.rollback();

                        if (reader != null)
                            reader.close();

                        if (errorItem - 1 < files.length) {
                            File oldFile = files[errorItem - 1];

                            String dirFolderRetry = props.getString(Constants.DIR_RETRY);

                            if (dirFolderRetry == null) {
                                logger.debug("PROPERTY URL FOLDER RETRY NOT CONFIG");
                                return;
                            }

                            File newFile = new File("retry" + "/" + oldFile.getName());

                            FileUtils.moveFile(oldFile, newFile);

                            if (!newFile.exists()) {
                                logger.debug("CANNOT COPY FILE UPDATE TO FOLDER RETRY");
                                return;
                            }

                            oldFile.delete();
                        }
                        logger.debug("MOVE DATA TO FOLDER RETRY SUCCESSFULLY!");
                    } finally {
                        if(reader != null)
                            reader.close();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null)
                connection.close();
            if (ps != null)
                ps.close();
            if (writer != null)
                writer.close();
        }

        long endTime = System.nanoTime();
        logger.debug("TIME TO EXECUTE BATCH:  {}", (endTime - startTime));
    }

    @Scheduled(cron = "0 */5 * ? * *")
    public void retryUpdateDbFromErrorFile() throws SQLException {
        logger.debug("************RUNNING RETRY WRITE LOG TO DB************");
        long startTime = System.nanoTime();
        logger.debug("DO TASK WRITE LOG TO DATABASE");

        File resource = new File(props.getString(Constants.DIR_RETRY));

        if (!resource.exists())
            logger.debug("RESOURCE RETRY NOT FOUND");

        File[] files = resource.listFiles();
        logger.debug("FOUND {} FILES IN FOLDER RETRY: {}", files == null ? 0 : files.length, props.getString(Constants.DIR_RETRY));

        String db = props.getString(Constants.DB_NAME);
        Connection connection = null;
        PreparedStatement ps = null;

        try {

            if (files != null) {

                try {
                    connection = connectDatabase.getConnection(db);
                    connection.setAutoCommit(false);
                    String sqlInsertLog1 = props.getString(Constants.ORACLE_SQL1);

                    if (sqlInsertLog1 == null) {
                        logger.debug("SQL_INSERT_LOG1 IS NULL");
                        return;
                    }

                    ps = connection.prepareStatement(sqlInsertLog1);
                } catch (Exception e) {
                    logger.debug("ERROR INIT CONNECTION POOL!");
                    return;
                }

                for (File fileItem : files) {

                    BufferedReader reader = null;
                    try {
                        reader = new BufferedReader(new FileReader(fileItem));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String[] lineColumn = line.split("\\t");

                            ps.setString(1, lineColumn[0]);
                            ps.setString(2, lineColumn[1]);
                            ps.setString(3, lineColumn[2]);
                            ps.setString(4, lineColumn[3]);
                            ps.setInt(5, Integer.parseInt(lineColumn[4]));
                            ps.setString(6, lineColumn[5]);
                            ps.setDate(7, new java.sql.Date(System.currentTimeMillis()));
                            ps.setString(8, lineColumn[6]);

                            ps.addBatch();
                        }

                        ps.executeBatch();

                        reader.close();

                        if (fileItem.delete())
                            logger.debug("FILE LOG TEMP DELETE SUCCESSFULLY!: {}", fileItem.getName());
                        else {
                            logger.debug("FILE DELETE FAILED!");
                            connection.rollback();
                        }

                        connection.commit();
                    } catch (Exception e1) {
                            connection.rollback();

                        if (reader != null)
                            reader.close();

                    } finally {
                        if (reader != null)
                            reader.close();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null)
                connection.close();
            if (ps != null)
                ps.close();
        }
    }
}
