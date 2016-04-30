
package cn.creditease.marmot.server.launch;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.servlets.gzip.GzipHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;

/**
 * embedded jetty server
 * @author aiweizhang(aiweizhang@creditease.cn)
 */

@Parameters(separators = "=")
public class Main {
  @Parameter(names = {"--port", "-p"}, description = "specify listening port")
  private int port = 8080;

  @Parameter(names = {"--host", "-H"}, description = "specify host")
  private String host = "0.0.0.0";

  @Parameter(names = {"--webapp", "-w"}, description = "webapp directory")
  private String resourceBase;

  @Parameter(names = {"--logs", "-L"}, description = "logs directory, relative to the \"webapp\" directory")
  private String logs = "logs";

  @Parameter(names = {"--help", "-h"}, help = true, description = "output help information")
  private boolean help;

  public static void main(String[] args) throws Exception {
    Main main = new Main();
    JCommander commander = new JCommander(main, args);

    if (main.help) {
      commander.usage();
      return;
    }

    // 检查端口号是否是一个有效的值
    if (main.port < 0 || main.port >= 65536) {
      throw new IllegalArgumentException("invalid port number: " + main.port);
    }

    // 检查resource base 是否是一个有效的值
    if (main.resourceBase == null || main.resourceBase.isEmpty()) {
      throw new IllegalArgumentException("invalid webapp path: " + main.resourceBase);
    }

    // 检查指定的webapp目录是否存在
    File webappFile = new File(main.resourceBase);
    if (!webappFile.exists() || !webappFile.isDirectory()) {
      throw new FileNotFoundException(main.resourceBase + ": no such directory");
    }

    // 当日志目录不存在的时候, 尝试创建一个
    String logsDir = main.resourceBase + File.separator + main.logs;
    File logsFile = new File(logsDir);
    if (!logsFile.exists() || !logsFile.isDirectory()) {
      if (!logsFile.mkdirs()) {
        throw new IOException("failed to create the" + logsDir);
      }
    }

    main.run(main.host, main.port, main.resourceBase, logsDir);
  }

  /**
   * 运行 jetty server
   * @throws Exception
   */
  public void run(String host, int port, String resourceBase, String logs) throws Exception {
    Server server = new Server(createThreadPool());

    MBeanContainer mbContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
    server.addBean(mbContainer);

    Configuration.ClassList classlist = Configuration.ClassList.setServerDefault(server);
    classlist.addBefore(
      "org.eclipse.jetty.webapp.JettyWebXmlConfiguration",
      "org.eclipse.jetty.annotations.AnnotationConfiguration"
    );

    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.setSendServerVersion(false);

    ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));

    connector.setPort(port);
    connector.setHost(host);

    server.addConnector(connector);
    server.setHandler(createHandlers(resourceBase, logs));

    server.start();
    server.join();
  }

  /**
   * 返回需要添加到server的处理器
   * @param resourceBase webapp路径
   * @param logs 日志输出路径
   * @return handler collection
   */
  private HandlerCollection createHandlers(String resourceBase, String logs) {
    HandlerCollection handlers = new HandlerCollection();
    handlers.setHandlers(new Handler[]{
      createAccessLog(logs),
      webappGzipWrapper(createWebApp(resourceBase))
    });

    return handlers;
  }

  /**
   * 创建线程池
   * @return thread pool
   */
  private ThreadPool createThreadPool() {
    QueuedThreadPool threadPool = new QueuedThreadPool();
    threadPool.setMinThreads(10);
    threadPool.setMaxThreads(200);
    threadPool.setIdleTimeout(60000);
    threadPool.setDetailedDump(false);
    return threadPool;
  }

  /**
   * 创建webapp
   * @param resourceBase webapp路径
   * @return webapp context
   */
  private WebAppContext createWebApp(String resourceBase) {
    WebAppContext webapp = new WebAppContext();

    webapp.setContextPath("/");
    webapp.setResourceBase(resourceBase);

    // not allow accessing directory
    webapp.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");


    // 配置加载到Jetty容器Classloader中的jar包的路径或匹配模式
    // 符合条件的jar包将会被检测META-INF、资源、tld和类的继承关系
    webapp.setAttribute(
      "org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
      ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/[^/]*taglibs.*\\.jar$"
    );
    return webapp;
  }

  /**
   * 创建访问日志的处理器
   * @param directory 日志的目录路径
   * @return request log handler
   */
  private RequestLogHandler createAccessLog(String directory) {
    RequestLogHandler logHandler = new RequestLogHandler();
    NCSARequestLog requestLog = new NCSARequestLog();

    requestLog.setFilename(directory + "/access-yyyy_mm_dd.log");
    requestLog.setFilenameDateFormat("yyyy_MM_dd");
    requestLog.setRetainDays(90);
    requestLog.setExtended(false);
    requestLog.setAppend(true);
    requestLog.setLogCookies(false);
    requestLog.setLogTimeZone("GMT");
    requestLog.setLogDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    logHandler.setRequestLog(requestLog);

    return logHandler;
  }

  /**
   * 创建gzip处理器
   * @return gzip handler
   */
  private GzipHandler webappGzipWrapper(WebAppContext webapp) {
    GzipHandler gzipHandler = new GzipHandler();
    gzipHandler.setMinGzipSize(1024);
    gzipHandler.addIncludedMimeTypes(
      "text/html",
      "text/htm",
      "application/json",
      "application/javascript",
      "application/x-javascript",
      "text/css",
      "application/xml",
      "image/svg+xml"
    );
    gzipHandler.setHandler(webapp);

    return gzipHandler;
  }

}