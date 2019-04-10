/**
 *
 */
package com.whlylc.web.concat;

import com.whlylc.web.util.StringUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * Utility to concat js and css
 *
 * @author Zeal
 */
public class ResourceConcat {

    //Logger
    private static final Logger logger = LogManager.getLogger(ResourceConcat.class);

    private static final Pattern CSS_URL_PATTERN = Pattern.compile("url\\([\\s]*['\"]?((?!['\"]?https?://|['\"]?data:|['\"]?/).*?)['\"]?[\\s]*\\)");

    private Charset resourceEncoding = StandardCharsets.UTF_8;

    //8K by default, response chunk size
    private int responseBufferSize = 8192;

    //Enable compression when web resource is >= compressionMinSize in bytes
    private int compressionMinSize = Integer.MAX_VALUE;

    //Web resource cache
    private Map<String, WebResource> resourceCache = new ConcurrentHashMap<>();

    //Concat resource cache
    private Map<String, ConcatResource> concatResourceCache = new ConcurrentHashMap<>();

    //Stop flag of watching thread
    private volatile boolean stopResourceWatcher = false;

    //Watching thread
    private ResourceWatcher watcherThread = null;

    //NIO watch service
    private WatchService watcher = null;

    //==================================================================================

    public ResourceConcat setCompressionMinSize(int compressionMinSize) {
        if (compressionMinSize <= 0) {
            throw new IllegalStateException("compressionMinSize is invalid");
        }
        this.compressionMinSize = compressionMinSize;
        return this;
    }

    public ResourceConcat setResourceEncoding(Charset resourceEncoding) {
        this.resourceEncoding = resourceEncoding;
        return this;
    }

    public ResourceConcat setResponseBufferSize(int size) {
        this.responseBufferSize = size;
        return this;
    }

    /**
     * Watch resource changes
     *
     * @param servletContext To get web app root path
     * @param resourcePaths  These paths must be under web app root path, relative paths
     * @return
     * @throws IOException
     */
    public ResourceConcat watchResources(ServletContext servletContext, String... resourcePaths) throws IOException {
        this.destroy();
        String rootDir = servletContext.getRealPath("/");
        if (logger.isInfoEnabled()) {
            logger.info("webapp dir is " + rootDir);
        }
        Path rootPath = Paths.get(rootDir);
        watcher = FileSystems.getDefault().newWatchService();
        Path[] paths = null;
        if (resourcePaths == null || resourcePaths.length <= 0) {
            paths = new Path[]{ rootPath };
        } else {
            paths = new Path[resourcePaths.length];
            for (int i = 0; i < resourcePaths.length; ++i) {
                paths[i] = Paths.get(rootDir, resourcePaths[i]);
            }
        }
        Path webInfDir = Paths.get(rootDir, "WEB-INF");
        for (Path path : paths) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.startsWith(webInfDir)) {
                        if (logger.isInfoEnabled()) {
                            logger.info(dir + " is monitoring......");
                        }
                        dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.OVERFLOW);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        this.watcherThread = new ResourceWatcher(rootPath);
        this.stopResourceWatcher = false;
        this.watcherThread.start();
        return this;
    }

    /**
     * Must call it before existing
     */
    public void destroy() {
        if (this.watcher != null) {
            try {
                this.watcher.close();
            } catch (IOException e) {
                logger.warn("Failed to close watcher service", e);
            }
        }
        this.stopResourceWatcher = true;
        if (this.watcherThread != null) {
            this.watcherThread.interrupt();
        }
    }

    /**
     * Resource watching thread
     */
    private class ResourceWatcher extends Thread {

        private Path rootPath = null;

        public ResourceWatcher(Path rootPath) {
            this.rootPath = rootPath;
        }

        public void run() {
            WatchKey key = null;
            while (!stopResourceWatcher) {
                try {
                    key = watcher.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        boolean create = event.kind().equals(StandardWatchEventKinds.ENTRY_CREATE);
                        boolean modify = event.kind().equals(StandardWatchEventKinds.ENTRY_MODIFY);
                        boolean delete = event.kind().equals(StandardWatchEventKinds.ENTRY_DELETE);
                        if (create || modify || delete) {
                            Path path = ((Path) key.watchable()).resolve((Path) event.context());
                            if (logger.isInfoEnabled()) {
                                logger.info("Resource " + path.toString() + " is changed, delete=" + delete + ",modify=" + modify + ",create=" + create);
                            }
                            if (Files.isDirectory(path) && !Files.isRegularFile(path)) {
                                continue;
                            }
                            String extName = FilenameUtils.getExtension(path.getFileName().toString());
                            if (!"js".equals(extName) && !"css".equals(extName)) {
                                continue;
                            }
                            String resourcePath = FilenameUtils.normalize(this.rootPath.relativize(path).toString(), true);
                            if (resourcePath.charAt(0) != '/') {
                                resourcePath = '/' + resourcePath;
                            }
                            WebResource resource = resourceCache.get(resourcePath);
                            if (resource != null) {
                                long newLastTime = Files.getLastModifiedTime(path).toMillis();
                                long oldLastTime = resource.getLastModified();
                                //logger.info("oldTime=" + oldLastTime + " ,newTime=" + newLastTime);
                                //Timestamp is not changed
                                if (newLastTime == oldLastTime) {
                                    continue;
                                }
                                if (create || modify) {
                                    byte[] bytes = Files.readAllBytes(path);
                                    resource.setContent(bytes);
                                    resource.setContentLength(bytes.length);
                                    resource.setLastModified(newLastTime);
                                } else {
                                    resource.setContent(new byte[]{});
                                    resource.setContentLength(0);
                                    resource.setLastModified(newLastTime);
                                }
                            }
                        } else {
                            //FIXME How to handle overflow event? Just ignore it right now
                            logger.warn("Overflow event, " + event.context());
                        }
                    }
                } catch (Throwable t) {
                    logger.error("Failed to handle watch service event", t);
                } finally {
                    if (key != null) {
                        key.reset();
                    }
                }
            }
        }
    }

    //===================================================================================

    /**
     * Concat web resources like js and css
     *
     * @param request
     * @param response
     * @throws IOException
     */
    public void concat(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String queryString = request.getQueryString();
        if (StringUtils.isBlank(queryString)) {
            return;
        }
        String concatResourcePath = queryString.trim();
        //First token as concat resource path
        int index = concatResourcePath.indexOf('&');
        if (index != -1) {
            concatResourcePath = concatResourcePath.substring(0, index).trim();
        }
        ConcatResource concatResource = this.concatResourceCache.get(concatResourcePath);
        if (concatResource == null) {
            concatResource = this.loadConcatResource(request, concatResourcePath);
        }
        //ETag is not modified
        if (!checkIfHeaders(request, response, concatResource)) {
            return;
        }
        //output content
        this.outputConcatResource(request, response, concatResource);
    }

    /**
     * Check if the conditions specified in the optional If headers are
     * satisfied.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resource The resource
     * @return <code>true</code> if the resource meets all the specified
     * conditions, and <code>false</code> if any of the conditions is not
     * satisfied, in which case request processing is stopped
     * @throws IOException an IO error occurred
     */
    protected boolean checkIfHeaders(HttpServletRequest request,
                                     HttpServletResponse response,
                                     ConcatResource resource)
            throws IOException {

        return checkIfMatch(request, response, resource)
                && checkIfModifiedSince(request, response, resource)
                && checkIfNoneMatch(request, response, resource)
                && checkIfUnmodifiedSince(request, response, resource);
    }

    /**
     * Check if the if-match condition is satisfied.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resource The resource
     * @return <code>true</code> if the resource meets the specified condition,
     * and <code>false</code> if the condition is not satisfied, in which case
     * request processing is stopped
     * @throws IOException an IO error occurred
     */
    private boolean checkIfMatch(HttpServletRequest request,
                                 HttpServletResponse response, ConcatResource resource) throws IOException {

        String eTag = resource.getETag();
        String headerValue = request.getHeader("If-Match");
        if (headerValue != null) {
            if (headerValue.indexOf('*') == -1) {

                StringTokenizer commaTokenizer = new StringTokenizer(headerValue, ",");
                boolean conditionSatisfied = false;

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

                // If none of the given ETags match, 412 Precondition failed is
                // sent back
                if (!conditionSatisfied) {
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if the if-modified-since condition is satisfied.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resource The resource
     * @return <code>true</code> if the resource meets the specified condition,
     * and <code>false</code> if the condition is not satisfied, in which case
     * request processing is stopped
     */
    private boolean checkIfModifiedSince(HttpServletRequest request,
                                         HttpServletResponse response, ConcatResource resource) {
        try {
            long headerValue = request.getDateHeader("If-Modified-Since");
            long lastModified = resource.getLastModified();
            if (headerValue != -1) {

                // If an If-None-Match header has been specified, if modified since
                // is ignored.
                if ((request.getHeader("If-None-Match") == null)
                        && (lastModified < headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader("ETag", resource.getETag());

                    return false;
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;
    }

    /**
     * Check if the if-none-match condition is satisfied.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resource The resource
     * @return <code>true</code> if the resource meets the specified condition,
     * and <code>false</code> if the condition is not satisfied, in which case
     * request processing is stopped
     * @throws IOException an IO error occurred
     */
    private boolean checkIfNoneMatch(HttpServletRequest request,
                                     HttpServletResponse response, ConcatResource resource)
            throws IOException {

        String eTag = resource.getETag();
        String headerValue = request.getHeader("If-None-Match");
        if (headerValue != null) {

            boolean conditionSatisfied = false;

            if (!headerValue.equals("*")) {

                StringTokenizer commaTokenizer =
                        new StringTokenizer(headerValue, ",");

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

            } else {
                conditionSatisfied = true;
            }

            if (conditionSatisfied) {

                // For GET and HEAD, we should respond with
                // 304 Not Modified.
                // For every other method, 412 Precondition Failed is sent
                // back.
                if (("GET".equals(request.getMethod()))
                        || ("HEAD".equals(request.getMethod()))) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader("ETag", eTag);

                    return false;
                }
                response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the if-unmodified-since condition is satisfied.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resource The resource
     * @return <code>true</code> if the resource meets the specified condition,
     * and <code>false</code> if the condition is not satisfied, in which case
     * request processing is stopped
     * @throws IOException an IO error occurred
     */
    private boolean checkIfUnmodifiedSince(HttpServletRequest request,
                                           HttpServletResponse response, ConcatResource resource)
            throws IOException {
        try {
            long lastModified = resource.getLastModified();
            long headerValue = request.getDateHeader("If-Unmodified-Since");
            if (headerValue != -1) {
                if (lastModified >= (headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;
    }

    /**
     * Output concat resource
     *
     * @param request
     * @param response
     * @param concatResource
     * @throws IOException
     */
    private void outputConcatResource(HttpServletRequest request,
                                      HttpServletResponse response, ConcatResource concatResource) throws IOException {

        if ("js".equalsIgnoreCase(concatResource.getResourceExtension())) {
            response.setContentType("application/javascript");
        } else if ("css".equalsIgnoreCase(concatResource.getResourceExtension())) {
            response.setContentType("text/css");
        }
        response.setContentLength((int) concatResource.getContentLength());
        // ETag header
        response.setHeader("ETag", concatResource.getETag());
        // Last-Modified header
        response.setHeader("Last-Modified", concatResource.getHttpLastModified());
        OutputStream outputStream = response.getOutputStream();
        boolean gzipSupport = this.isCompressionSupported(request, concatResource.getContentLength());
        try {
            //Gzip output stream
            if (gzipSupport) {
                //Output chunk
                response.setContentLength(-1);
                response.setBufferSize(this.responseBufferSize);
                response.addHeader("Content-Encoding", "gzip");
                outputStream = new GZIPOutputStream(outputStream);
            }
            else {
                response.setContentLength((int) concatResource.getContentLength());
            }
            byte[] line = new byte[]{'\r', '\n'};
            byte[] buffer = new byte[1024];
            int readCount = 0;
            ByteArrayInputStream inputStream = null;
            for (WebResource resource : concatResource.getResources()) {
                inputStream = new ByteArrayInputStream(resource.getContent());
                while ((readCount = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, readCount);
                }
                outputStream.write(line);
            }
            outputStream.flush();
        } finally {
            if (gzipSupport) {
                outputStream.close();
            }
        }
    }

    /**
     * Check whether enable gzip
     *
     * @param request
     * @param resourceContentLength
     * @return
     */
    private boolean isCompressionSupported(HttpServletRequest request, long resourceContentLength) {
        Enumeration<String> e = request.getHeaders("Accept-Encoding");
        while (e.hasMoreElements()) {
            String value = e.nextElement();
            if (value.indexOf("gzip") != -1) {
                return resourceContentLength >= this.compressionMinSize;
            }
        }
        return false;
    }

    /**
     * Get context path whose style is not started with /
     *
     * @param request
     * @return
     */
    private String getContextPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        if (contextPath.length() == 1 && contextPath.charAt(0) == '/') {
            contextPath = "";
        }
        return contextPath;
    }

    /**
     * Load concat resource
     *
     * @param request
     * @param concatResourcePath
     * @return
     */
    private ConcatResource loadConcatResource(HttpServletRequest request, String concatResourcePath) {

        List<String> tokens = StringUtils.tokenizeToStringList(concatResourcePath, ";", true, true);
        ConcatResource concatResource = new ConcatResource(concatResourcePath);
        List<WebResource> resources = new ArrayList<>();
        String contextPath = null;

        for (String token : tokens) {
            List<String> resourcePaths = StringUtils.tokenizeToStringList(token, ",", true, true);
            if (resourcePaths == null || resourcePaths.size() <= 0) {
                continue;
            }
            String dir = null;
            for (int i = 0; i < resourcePaths.size(); ++i) {
                String resourcePath = FilenameUtils.normalize(resourcePaths.get(i), true);
                if (i == 0) {
                    if (resourcePath.charAt(0) != '/') {
                        resourcePath = '/' + resourcePath;
                    }
                    dir = FilenameUtils.getFullPath(resourcePath);
                } else {
                    resourcePath = dir + resourcePath;
                }
                WebResource resource = resourceCache.get(resourcePath);
                if (resource == null) {
                    resource = this.loadWebResource(request, resourcePath);
                    if (resource == null) {
                        continue;
                    }
                    if (resource.getContentLength() > 0 && "css".equals(resource.getResourceExtension())) {
                        if (contextPath == null) {
                            contextPath = this.getContextPath(request);
                        }
                        this.fixCssUrl(resource, contextPath);
                    }
                    resourceCache.put(resource.getResourcePath(), resource);
                }
                resources.add(resource);
            }
        }
        concatResource.setResources(resources);
        this.concatResourceCache.put(concatResourcePath, concatResource);
        return concatResource;
    }

    /**
     * Load web resource
     *
     * @param request
     * @param resourcePath
     * @return If there's errors return null
     */
    private WebResource loadWebResource(HttpServletRequest request, String resourcePath) {
        File file = new File(request.getServletContext().getRealPath(resourcePath));
        String extName = FilenameUtils.getExtension(file.getName());
        if (!"js".equals(extName) && !"css".equals(extName)) {
            logger.warn("Unsupported resource file " + resourcePath);
            return null;
        }
        if (resourcePath.contains("WEB-INF")) {
            logger.warn("Invalid resource file " + resourcePath);
            return null;
        }
        WebResource resource = new WebResource();
        resource.setResourcePath(resourcePath);
        resource.setResourceName(file.getName());
        resource.setResourceExtension(extName);
        if (!file.exists() || !file.isFile()) {
            return resource;
        }
        resource.setContentLength(file.length());
        resource.setLastModified(file.lastModified());
        resource.setResourceEncoding(this.resourceEncoding);
        byte[] content = null;
        try {
            //jsContent = FileUtils.readFileToString(file, this.resourceEncoding);
            content = FileUtils.readFileToByteArray(file);
        } catch (IOException e) {
            logger.warn("Failed to read " + file.getAbsolutePath());
            return null;
        }
        resource.setContent(content);
        return resource;
    }

    /**
     * Fix css url
     *
     * @param resource
     */
    private void fixCssUrl(WebResource resource, String contextPath) {
        byte[] content = resource.getContent();
        if (content == null || content.length <= 0) {
            return;
        }
        //String cssContent = resource.getContent();
        String cssContent = new String(content, resource.getResourceEncoding());
        if (StringUtils.isEmpty(cssContent)) {
            return;
        } else {
            Matcher matcher = CSS_URL_PATTERN.matcher(cssContent);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String url = matcher.group(1).trim();
                url = contextPath + FilenameUtils.getFullPath(resource.getResourcePath()) + url;
                url = FilenameUtils.normalize(url, true);
                StringBuilder cssPath = new StringBuilder("url('").append(url).append("')");
                matcher.appendReplacement(sb, cssPath.toString());
            }
            cssContent = sb.toString();
            //resource.setContent(cssContent);
            content = cssContent.getBytes(resource.getResourceEncoding());
            resource.setContent(content);
            resource.setContentLength(content.length);
        }
    }

}
