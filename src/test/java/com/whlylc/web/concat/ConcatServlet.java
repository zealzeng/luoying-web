package com.whlylc.web.concat;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Created by Zeal on 2019/4/9 0009.
 */
public class ConcatServlet extends HttpServlet {

    private ResourceConcat resourceConcat = null;

    @Override
    public void init() throws ServletException {
        ServletContext servletContext = this.getServletContext();
        resourceConcat = new ResourceConcat();
        //默认不开启gzip,资源文件超过最小值(这里设置10K)才生效
        resourceConcat.setCompressionMinSize(10 * 1024);
        //资源文件最好能统一个编码,建议为默认的UTF-8
        resourceConcat.setResourceEncoding(StandardCharsets.UTF_8);
        //开启gzip时使用的Transfer-Encoding为chunked,无gzip时Content-Length才设置,这个参数通常不用设置
        //resourceConcat.setResponseBufferSize(10 * 1024);
        try {
            //开启资源文件监控,使用NIO WatchService,可实时更新内存中的缓存的资源文件内容,默认采访过的js,css加载到内存.
            //watchResources()方法不指定特定目录则监控除去WEB-INF外的所有文件夹
            resourceConcat.watchResources(this.getServletContext(), "/js", "/css");
        } catch (IOException e) {
            throw new ServletException(e);
        }
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resourceConcat.concat(req, resp);
    }

}
