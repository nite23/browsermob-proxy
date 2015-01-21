package net.lightbody.bmp.proxy.rest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.proxy.BlacklistEntry;
import net.lightbody.bmp.proxy.ProxyManager;
import net.lightbody.bmp.proxy.ProxyServer;
import net.lightbody.bmp.proxy.http.BrowserMobHttpRequest;
import net.lightbody.bmp.proxy.http.BrowserMobHttpResponse;
import net.lightbody.bmp.proxy.http.RequestInterceptor;
import net.lightbody.bmp.proxy.http.ResponseInterceptor;
import org.java_bandwidthlimiter.StreamManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Path("proxy")
@Consumes(MediaType.WILDCARD)
@Produces(MediaType.APPLICATION_JSON)
public class ProxyResource {
    private static final Logger LOG = LoggerFactory.getLogger(ProxyResource.class);

    private final ProxyManager proxyManager;

    @Inject
    public ProxyResource(ProxyManager proxyManager) {
        this.proxyManager = proxyManager;
    }

    @GET
    @Path("/")
    public ProxyListDescriptor getProxies() {
        Collection<ProxyDescriptor> proxyList = new ArrayList<ProxyDescriptor> ();
        for (ProxyServer proxy : proxyManager.get()) {
            proxyList.add(new ProxyDescriptor(proxy.getPort()));
        }
        return new ProxyListDescriptor(proxyList);
    }
           
    @POST
    @Path("/")
    public ProxyDescriptor newProxy(
            @FormParam("httpProxy") String httpProxy,
            @FormParam("bindAddress") String paramBindAddr, 
            @FormParam("port") Integer paramPort        
    ) {
        String systemProxyHost = System.getProperty("http.proxyHost");
        String systemProxyPort = System.getProperty("http.proxyPort");        
        Hashtable<String, String> options = new Hashtable<String, String>();

        // If the upstream proxy is specified via query params that should override any default system level proxy.
        if (httpProxy != null) {
            options.put("httpProxy", httpProxy);
        } else if ((systemProxyHost != null) && (systemProxyPort != null)) {
            options.put("httpProxy", String.format("%s:%s", systemProxyHost, systemProxyPort));
        }
        LOG.debug("POST proxy instance on bindAddress `{}` & port `{}`", 
                paramBindAddr, paramPort);
        ProxyServer proxy = proxyManager.create(options, paramPort, paramBindAddr);            
        return new ProxyDescriptor(proxy.getPort());
    }

    @GET
    @Path("/{port}/har")
    public Har getHar(@PathParam("port") int port) {
        ProxyServer proxy = proxyManager.get(port);
        return proxy.getHar();
    }

    @POST
    @Path("/{port}/har")
    public Response newHar(
            @PathParam("port") int port,
            @FormParam("initialPageRef") String initialPageRef,
            @FormParam("captureHeaders") boolean captureHeaders,
            @FormParam("captureContent") boolean captureContent,
            @FormParam("captureBinaryContent") boolean captureBinaryContent
    ) {
        ProxyServer proxy = proxyManager.get(port);        
        Har oldHar = proxy.newHar(initialPageRef);                        
        proxy.setCaptureHeaders(captureHeaders);
        proxy.setCaptureContent(captureContent);
        proxy.setCaptureBinaryContent(captureBinaryContent); 

        if (oldHar != null) {
            return Response.ok(oldHar).type(MediaType.APPLICATION_JSON_TYPE).build();
        } else {
            return Response.noContent().build();
        }
    }

    @PUT
    @Path("/{port}/har/pageRef")
    public void setPage(
            @PathParam("port") int port, 
            @FormParam("pageRef") String pageRef
    ) {
        ProxyServer proxy = proxyManager.get(port);
        proxy.newPage(pageRef);
    }

    @GET
    @Path("/{port}/blacklist")
    public Collection<BlacklistEntry> getBlacklist(@PathParam("port") int port) {
        ProxyServer proxy = proxyManager.get(port);
        return proxy.getBlacklistedUrls();
    }

    @PUT
    @Path("/{port}/blacklist")
    public void blacklist(
            @PathParam("port") int port,
            @FormParam("regex") String blacklist,
            @FormParam("status") String status,
            @FormParam("method") String method
    ) {
        ProxyServer proxy = proxyManager.get(port);
        proxy.blacklistRequests(blacklist, parseResponseCode(status), method);        
    }
    
    @DELETE
    @Path("/{port}/blacklist")
    public void clearBlacklist(@PathParam("port") int port) {
        ProxyServer proxy = proxyManager.get(port);
        proxy.clearBlacklist();        
    }

    @GET
    @Path("/{port}/whitelist")
    public Collection<Pattern> getWhitelist(@PathParam("port") int port) {
        ProxyServer proxy = proxyManager.get(port);
        return proxy.getWhitelistUrls();
    }

    @PUT
    @Path("/{port}/whitelist")
    public void whitelist(
            @PathParam("port") int port,
            @FormParam("regex") String regex,
            @FormParam("status") String status,
            @FormParam("method") String method
    ) {
        ProxyServer proxy = proxyManager.get(port);        
        int responseCode = parseResponseCode(status);
        proxy.whitelistRequests(regex.split(","), responseCode);
    }
    
    @DELETE
    @Path("/{port}/whitelist")
    public void clearWhitelist(@PathParam("port") int port) {
        ProxyServer proxy = proxyManager.get(port);
        proxy.clearWhitelist();        
    }

    @POST
    @Path("/{port}/auth/basic/{domain}")
    public void autoBasicAuth(
            @PathParam("port") int port, 
            @PathParam("domain") String domain,
            @FormParam("username") String username,
            @FormParam("password") String password
    ) {
        ProxyServer proxy = proxyManager.get(port);        
        proxy.autoBasicAuthorization(domain, username, password);
    }

    @POST
    @Path("/{port}/headers")
    public void updateHeaders(@PathParam("port") int port, Form form) {
        ProxyServer proxy = proxyManager.get(port);
                
        for (Map.Entry<String, List<String>> entry : form.asMap().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().get(0);
            proxy.addHeader(key, value);
        }
    }

    @POST
    @Path("/{port}/interceptor/response")
    public void addResponseInterceptor(@PathParam("port") int port, Reader reader) throws IOException, ScriptException {
        
        ProxyServer proxy = proxyManager.get(port);
        
        ScriptEngineManager mgr = new ScriptEngineManager();
        final ScriptEngine engine = mgr.getEngineByName("JavaScript");
        Compilable compilable = (Compilable)  engine;
        final CompiledScript script = compilable.compile(reader);

        proxy.addResponseInterceptor(new ResponseInterceptor() {
            @Override
            public void process(BrowserMobHttpResponse response, Har har) {
                Bindings bindings = engine.createBindings();
                bindings.put("response", response);
                bindings.put("har", har);
                bindings.put("log", LOG);
                try {
                    script.eval(bindings);
                } catch (ScriptException e) {
                    LOG.error("Could not execute JS-based response interceptor", e);
                }
            }
        });
    }

    @POST
    @Path("/{port}/interceptor/request")
    public void addRequestInterceptor(@PathParam("port") int port, Reader reader) throws IOException, ScriptException {
        ProxyServer proxy = proxyManager.get(port);

        ScriptEngineManager mgr = new ScriptEngineManager();
        final ScriptEngine engine = mgr.getEngineByName("JavaScript");
        Compilable compilable = (Compilable)  engine;
        final CompiledScript script = compilable.compile(reader);

        proxy.addRequestInterceptor(new RequestInterceptor() {
            @Override
            public void process(BrowserMobHttpRequest request, Har har) {
                Bindings bindings = engine.createBindings();
                bindings.put("request", request);
                bindings.put("har", har);
                bindings.put("log", LOG);
                try {
                    script.eval(bindings);
                } catch (ScriptException e) {
                    LOG.error("Could not execute JS-based response interceptor", e);
                }
            }
        });       
    }

    @PUT
    @Path("/{port}/limit")
    public void limit(
            @PathParam("port") int port,
            @FormParam("upstreamKbps") Integer upstreamKbps,
            @FormParam("downstreamKbps") Integer downstreamKbps,
            @FormParam("upstreamMaxKB") Integer upstreamMaxKB,
            @FormParam("downstreamMaxKB") Integer downstreamMaxKB,
            @FormParam("latency") Integer latency,
            @FormParam("payloadPercentage") Integer payloadPercentage,
            @FormParam("maxBitsPerSecond") Integer maxBitsPerSecond,
            @FormParam("enable") Boolean enable
    ) {
        ProxyServer proxy = proxyManager.get(port);

        StreamManager streamManager = proxy.getStreamManager();        
        if (upstreamKbps != null) {            
            streamManager.setUpstreamKbps(upstreamKbps);
            streamManager.enable();            
        }        
        if (downstreamKbps != null) {
            streamManager.setDownstreamKbps(downstreamKbps);
            streamManager.enable();            
        }        
        if (upstreamMaxKB != null) {
            streamManager.setUpstreamMaxKB(upstreamMaxKB);
            streamManager.enable();          
        }
        if (downstreamMaxKB != null) {
            streamManager.setDownstreamMaxKB(downstreamMaxKB);
            streamManager.enable();
        }                
        if (latency != null) {
            streamManager.setLatency(latency);
            streamManager.enable();            
        }        
        if (payloadPercentage != null) {
            streamManager.setPayloadPercentage(payloadPercentage);
        }
        if (maxBitsPerSecond != null) {
            streamManager.setMaxBitsPerSecondThreshold(maxBitsPerSecond);
        }
        if (enable != null) {
            if( enable ) {
                streamManager.enable();
            } else {
                streamManager.disable();
            }
        }
    }
    
    @GET
    @Path("/{port}/limit")
    public BandwidthLimitDescriptor getLimits(@PathParam("port") int port) {
        ProxyServer proxy = proxyManager.get(port);
        return new BandwidthLimitDescriptor(proxy.getStreamManager());
    }
    
    @PUT
    @Path("/{port}/timeout")
    public void timeout(
            @PathParam("port") int port,
            @FormParam("requestTimeout") Integer requestTimeout,
            @FormParam("readTimeout") Integer readTimeout,
            @FormParam("connectionTimeout") Integer connectionTimeout,
            @FormParam("dnsCacheTimeout") Integer dnsCacheTimeout
    ) {
        ProxyServer proxy = proxyManager.get(port);

        if (requestTimeout != null) {
            proxy.setRequestTimeout(requestTimeout);            
        }        
        if (readTimeout != null) {
            proxy.setSocketOperationTimeout(readTimeout);            
        }        
        if (connectionTimeout != null) {
            proxy.setConnectionTimeout(connectionTimeout);            
        }
        if (dnsCacheTimeout != null) {
            proxy.setDNSCacheTimeout(dnsCacheTimeout);            
        }
    }

    @DELETE
    @Path("/{port}")
    public void delete(@PathParam("port") int port) {
        proxyManager.get(port);
        proxyManager.delete(port);        
    }

    @POST
    @Path("/{port}/hosts")
    public void remapHosts(@PathParam("port") int port, Form form) {
        ProxyServer proxy = proxyManager.get(port);
        
        for (Map.Entry<String, List<String>> entry : form.asMap().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().get(0);
            proxy.remapHost(key, value);
            proxy.setDNSCacheTimeout(0);
            proxy.clearDNSCache();
        }
    }


    @PUT
    @Path("/{port}/wait")
    public void wait(
            @PathParam("port") int port,
            @FormParam("quietPeriodInMs") Integer quietPeriodInMs,
            @FormParam("timeoutInMs") Integer timeoutInMs
    ) {
        ProxyServer proxy = proxyManager.get(port);        
        proxy.waitForNetworkTrafficToStop(quietPeriodInMs, timeoutInMs);        
    }
    
    @DELETE
    @Path("/{port}/dns/cache")
    public void clearDnsCache(@PathParam("port") int port) {
        ProxyServer proxy = proxyManager.get(port);
    	proxy.clearDNSCache();
    }

    @PUT
    @Path("/{port}/rewrite")
    public void rewriteUrl(
            @PathParam("port") int port,
            @FormParam("matchRegex") String match,
            @FormParam("replace") String replace
    ) {
        ProxyServer proxy = proxyManager.get(port);        
        proxy.rewriteUrl(match, replace);
    } 
    
    @DELETE
    @Path("/{port}/rewrite")
    public void clearRewriteRules(@PathParam("port") int port) {
        ProxyServer proxy = proxyManager.get(port);
        proxy.clearRewriteRules();    	
    }
    
    @PUT
    @Path("/:port/retry")
    public void retryCount(
            @PathParam("port") int port, 
            @FormParam("retrycount") Integer count
    ) {
        ProxyServer proxy = proxyManager.get(port);
        proxy.setRetryCount(count);
    }
    
    private int parseResponseCode(String response) {
        int responseCode = 200;
        if (response != null) {
            try {
                responseCode = Integer.parseInt(response);
            } catch (NumberFormatException e) { }
        }
        return responseCode;
    }

    public static class ProxyDescriptor {
        private int port;

        public ProxyDescriptor() {
        }

        public ProxyDescriptor(int port) {
            this.port = port;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static class ProxyListDescriptor {
        private Collection<ProxyDescriptor> proxyList;

        public ProxyListDescriptor() {
        }

        public ProxyListDescriptor(Collection<ProxyDescriptor> proxyList) {
            this.proxyList = proxyList;
        }

        public Collection<ProxyDescriptor> getProxyList() {
            return proxyList;
        }

        public void setProxyList(Collection<ProxyDescriptor> proxyList) {
            this.proxyList = proxyList;
        }
    }
           
    public static class BandwidthLimitDescriptor {
        private long maxUpstreamKB;
        private long remainingUpstreamKB;
        private long maxDownstreamKB;
        private long remainingDownstreamKB;
        
        public BandwidthLimitDescriptor(){
        }
        
        public BandwidthLimitDescriptor(StreamManager manager){
            this.maxDownstreamKB = manager.getMaxDownstreamKB();
            this.remainingDownstreamKB = manager.getRemainingDownstreamKB();
            this.maxUpstreamKB = manager.getMaxUpstreamKB();
            this.remainingUpstreamKB = manager.getRemainingUpstreamKB();
        }

        public long getMaxUpstreamKB() {
            return maxUpstreamKB;
        }

        public void setMaxUpstreamKB(long maxUpstreamKB) {
            this.maxUpstreamKB = maxUpstreamKB;
        }

        public long getRemainingUpstreamKB() {
            return remainingUpstreamKB;
        }

        public void setRemainingUpstreamKB(long remainingUpstreamKB) {
            this.remainingUpstreamKB = remainingUpstreamKB;
        }

        public long getMaxDownstreamKB() {
            return maxDownstreamKB;
        }

        public void setMaxDownstreamKB(long maxDownstreamKB) {
            this.maxDownstreamKB = maxDownstreamKB;
        }

        public long getRemainingDownstreamKB() {
            return remainingDownstreamKB;
        }

        public void setRemainingDownstreamKB(long remainingDownstreamKB) {
            this.remainingDownstreamKB = remainingDownstreamKB;
        }        
    }
}
