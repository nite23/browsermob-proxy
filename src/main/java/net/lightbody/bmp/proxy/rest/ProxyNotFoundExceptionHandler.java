package net.lightbody.bmp.proxy.rest;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import net.lightbody.bmp.proxy.ProxyNotFoundException;

@Provider
public class ProxyNotFoundExceptionHandler implements ExceptionMapper<ProxyNotFoundException>{   

    @Override
    public Response toResponse(ProxyNotFoundException e) {
        return Response.status(Status.NOT_FOUND).build();
    }
}