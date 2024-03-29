import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import base.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Extraction extends SimpleTask {

  private final String url;

  public Extraction(String url) throws IOException {
    stageName = "extraction";
    logger = Logger.getLogger(Extraction.class);

    this.url = url;
    try {
      appProps = loadConfig();
    } catch (IOException e) {
      logger.error("Can't initialize Extraction task: can't load config");
      throw e;
    }
  }

  @Override
  public List<Task> getDependencies() {
    return new ArrayList<>();
  }

  @Override
  public TaskResultStatus run() {
    try {
      List<String> extractedLinks = extractLinks(url);
      writeToHdfs(extractedLinks);
      return TaskResultStatus.Success;
    } catch (Exception e) {
      logger.error(e);
      return TaskResultStatus.Error;
    }
  }

  private List<String> extractLinks(String url) throws IOException {
    List<String> extractedUrls = new ArrayList<>();
    Document doc = Jsoup.connect(url).get();
    Elements links = doc.select("a");
    for (Element link : links) {
      extractedUrls.add(link.absUrl("href"));
    }

    return extractedUrls;
  }

  private void writeToHdfs(List<String> links) throws IOException {
    logger.info("Trying to write to HDFS...");

    logger.info("Initializing HDFS File System Object...");
    Configuration conf = new Configuration();
    conf.set("fs.hdfs.impl", org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
    conf.set("fs.file.impl", org.apache.hadoop.fs.LocalFileSystem.class.getName());

    System.setProperty("HADOOP_USER_NAME", "kharivitalij");
    System.setProperty("hadoop.home.dir", "/");

    FileSystem fs = FileSystem.get(URI.create(appProps.getProperty("hdfsPrefix")), conf);

    Path outputFileName = new Path(getOutput().getPath());
    logger.info("base.Output file name: " + outputFileName);
    if (fs.exists(outputFileName)) {
      logger.warn(
          "base.Output file " + outputFileName + " already exists, WILL NOT run Extraction task.");
      return;
    }

    logger.info("Begin Write file into hdfs...");
    FSDataOutputStream outputStream = fs.create(outputFileName);
    for (String link : links) {
      outputStream.writeBytes(link + "\n");
    }
    outputStream.close();
    logger.info("Successfully writen " + links.size() + " strings.");
  }

  @Override
  public Output getOutput() {
    final String fsname = appProps.getProperty("hdfsPrefix");
    final String username = System.getProperty("user.name");
    final String appName = appProps.getProperty("appName");
    final String appLang = appProps.getProperty("appLang");

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
    final String dateTime = sdf.format(new Date(System.currentTimeMillis()));

    final String stage = stageName;
    final String filename = url.replaceAll("[/:.]", "_");

    final String outputPath =
        String.format(
            "%s/users/%s/%s/%s/%s/%s/%s",
            fsname, username, appName, appLang, dateTime, stage, filename);

    return new Output(OutputType.HDFS, outputPath);
  }
}
