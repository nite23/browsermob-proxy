package net.lightbody.bmp.proxy.guice;
import com.google.inject.Binder;
import com.google.inject.Module;
import net.lightbody.bmp.core.har.HarPage;
import net.lightbody.bmp.proxy.rest.ProxyExistsExceptionHandler;
import net.lightbody.bmp.proxy.rest.ProxyNotFoundExceptionHandler;
import net.lightbody.bmp.proxy.rest.ProxyPortsExhaustedExceptionHandler;
import net.lightbody.bmp.proxy.rest.ProxyResource;

public class RestModule implements Module {
    
    @Override
    public void configure(final Binder binder){        
        binder.bind(ProxyNotFoundExceptionHandler.class);
        binder.bind(ProxyExistsExceptionHandler.class);
        binder.bind(ProxyPortsExhaustedExceptionHandler.class);
        binder.bind(HarPage.class);
        binder.bind(ProxyResource.class);
    }
}
