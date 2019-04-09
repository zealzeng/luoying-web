package com.whlylc.web.concat;

import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Concat web resource
 * Created by Zeal on 2019/3/9 0009.
 */
public class ConcatResource {

    private static final String RFC1123_DATE = "EEE, dd MMM yyyy HH:mm:ss zzz";

    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    private String resourcePath = null;

    private String resourceExtension = null;

    private List<WebResource> resources = null;

    public ConcatResource(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public List<WebResource> getResources() {
        return resources;
    }

    public void setResources(List<WebResource> resources) {
        this.resources = resources;
        if (this.resources != null && this.resources.size() > 0) {
            this.resourceExtension = this.resources.get(0).getResourceExtension();
        }
    }

    public String getResourceExtension() {
        return resourceExtension;
    }

    public long getContentLength() {
        if (this.resources == null || this.resources.size() <= 0) {
            return 0;
        }
        long totalLength = 0;
        for (WebResource resource : resources) {
            totalLength += resource.getContentLength();
            //Append \r\n
            totalLength += 2;
        }
        return totalLength;
    }

    public long getLastModified() {
        if (this.resources == null || this.resources.size() <= 0) {
            return 0;
        }
        long lastModifiedTime = 0;
        for (WebResource resource : resources) {
            lastModifiedTime += resource.getLastModified();
        }
        return lastModifiedTime / resources.size();
    }

    public String getETag() {
        StringBuilder sb = new StringBuilder();
        sb.append("W/\"").append(this.getContentLength()).append('-').append(this.getLastModified()).append("\"");
        return sb.toString();
    }

    public String getHttpLastModified() {
        return DateFormatUtils.format(this.getLastModified(), RFC1123_DATE, GMT, Locale.US);
    }
}
