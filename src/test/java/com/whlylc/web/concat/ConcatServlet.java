package com.whlylc.web.concat;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by Zeal on 2019/4/9 0009.
 */
public class ConcatServlet extends HttpServlet {

    private ResourceConcat resourceConcat = null;

    @Override
    public void init() throws ServletException {
        ServletContext servletContext = this.getServletContext();
        resourceConcat = new ResourceConcat();
        //resourceConcat.setCompressionMinSize()
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

    }

}
