package com.whlylc.web.concat;

import java.nio.charset.Charset;

/**
 * Single web resource
 * Created by Zeal on 2019/3/9 0009.
 */
public class WebResource {

    //private File resourceRoot = null;

    private String resourcePath = null;

    private String resourceName = null;

    //Extension name
    private String resourceExtension = null;

    private byte[] content = null;

    private Charset resourceEncoding = null;

    private long contentLength = 0;

    //File last modified time
    private long lastModified = 0;

    public String getResourceName() {
        return resourceName;
    }

    public String getResourceExtension() {
        return resourceExtension;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public void setResourceExtension(String resourceExtension) {
        this.resourceExtension = resourceExtension;
    }

    public Charset getResourceEncoding() {
        return resourceEncoding;
    }

    public void setResourceEncoding(Charset resourceEncoding) {
        this.resourceEncoding = resourceEncoding;
    }
}
