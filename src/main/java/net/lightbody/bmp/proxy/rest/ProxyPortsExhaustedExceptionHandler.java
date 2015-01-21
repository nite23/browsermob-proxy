package net.lightbody.bmp.proxy.rest;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import net.lightbody.bmp.proxy.ProxyPortsExhaustedException;

@Provider
public class ProxyPortsExhaustedExceptionHandler implements ExceptionMapper<ProxyPortsExhaustedException>{
    
    @Override
    public Response toResponse(ProxyPortsExhaustedException e) {
        return Response.status(456).build();
    }
}
