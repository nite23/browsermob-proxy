package net.lightbody.bmp.proxy.rest;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import net.lightbody.bmp.proxy.ProxyExistsException;

@Provider
public class ProxyExistsExceptionHandler implements ExceptionMapper<ProxyExistsException>{

    @Override
    public Response toResponse(ProxyExistsException e) {         
        return Response.status(455).type(MediaType.APPLICATION_JSON).entity(
                new ProxyResource.ProxyDescriptor(e.getPort())
        ).build();
    }
    
}
