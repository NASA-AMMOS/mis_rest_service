package jpl.mipl.mars.mis;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.ClientAbortException;
import org.apache.http.conn.ConnectionPoolTimeoutException;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Marsette Vona
 */
public class MISService extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final boolean DEF_ENABLE_S3 = true;
    public static final boolean DEF_ENABLE_HTTP = true;

    private static final Logger log = LoggerFactory.getLogger(MISService.class);

    public static final String DEF_AWS_REGION = "us-gov-west-1";

    //most stuff is either final or volatile
    //because (afaik) Tomcat may call doGet() from multiple threads
    //most of this is actually set by init() and immutable thereafter
    //but unfortunately we can't use final because init() is not a constructor
    //so using volatile just to ensure safe publication of the values from the init() thread to the doGet() threads

    private volatile String awsProfile;
    private volatile String awsRegion;
    private volatile S3Helper.UseAWSHeader useAWSHeader;
    private volatile boolean enableS3;
    private volatile S3Helper s3;
    private volatile boolean enableHTTP;

    private volatile String mission;

    private volatile int maxConcurrentRequests;
    private volatile int concurrentRequests;
    private final Object requestCountLock = new Object();

    @Override
    public void init() throws ServletException {

        //mars.jar contains a META-INF/services/javax.xml.parsers.DocumentBuilderFactory
        //that specifies an old xerces implementation for javax.xml.parsers.DocumentBuilderFactory
        //we remove that file from image_sampler.jar in our pom.xml
        //but it appears more tricky to do same for image_sampler.war
        //because in that case the whole mars.jar is included as is
        //so for the war we override the services file here
        System.setProperty("javax.xml.parsers.DocumentBuilderFactory",
                           "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");

        String pfx = MISTask.getVersion() + " ";

        log.info(pfx + "image sampler version " + MISTask.getVersion(getClass()));

        String profile = getInitParameter("aws_profile");
        awsProfile = profile != null && !profile.trim().isEmpty() ? profile.trim() : "null";

        String region = getInitParameter("aws_region");
        awsRegion = region != null && !region.trim().isEmpty() ? region.trim() : DEF_AWS_REGION;

        Runtime rt = Runtime.getRuntime();
        log.info(pfx + rt.availableProcessors() + " cores, " + (int)(rt.maxMemory() * 1e-6) + "MB");

        MISTask.debug = MISParams.parseBool(getInitParameter("debug_sampler"));
        if (MISTask.debug) {
            MISTask.setLogLevel(MISTask.log, "DEBUG");
            MISTask.setLogLevel(log, "DEBUG");
        }

        maxConcurrentRequests = MISParams.parseInt(getInitParameter("sampler_max_concurrent_requests"), -1);
        log.info(pfx + "max concurrent requests: " + maxConcurrentRequests);

        useAWSHeader = MISParams.parseEnum(getInitParameter("use_aws_header"), "use_aws_header",
                                           S3Helper.UseAWSHeader.class, S3Helper.UseAWSHeader.prefer);

        enableS3  = MISParams.parseBool(getInitParameter("enable_s3"), DEF_ENABLE_S3);
        log.info(pfx + "enable S3: " + enableS3);

        if (enableS3) {
            initS3(pfx);
        }

        enableHTTP  = MISParams.parseBool(getInitParameter("enable_http"), DEF_ENABLE_HTTP);
        log.info(pfx + "enable HTTP: " + enableHTTP);

        String ignore = getInitParameter("ignore_subdirs");
        if (ignore != null && ignore.trim().length() > 0) {
            MISTask.ignoreSubdirs = ignore.trim().split(",");
        }

        String whitelist = getInitParameter("rdr_url_whitelist_patterns");
        if (whitelist != null && whitelist.trim().length() > 0) {
            String[] regex = whitelist.trim().split(",");
            List<Pattern> patterns = new ArrayList<Pattern>();
            for (int i = 0; i < regex.length; i++) {
                if (regex[i].length() > 0) {
                    try {
                        patterns.add(Pattern.compile(regex[i]));
                    } catch (PatternSyntaxException ex) {
                        log.error("error in whitelist pattern " + i + ": " + ex.getDescription(),
                                  ex.getPattern(), ex.getIndex());
                    }
                }
            }
            MISTask.rdrUrlWhitelistPatterns = patterns.toArray(new Pattern[0]);
        }

        mission = ExplicitMISParams.checkMission(getInitParameter("mission"));
        log.info(pfx + "mission: " + mission);

        MISTask.maxCachedSamples = MISParams.parseInt(getInitParameter("max_cached_samples"),
                                                      SparseImage.DEF_MAX_CACHED_SAMPLES);
        log.info(pfx + "max cached samples: " + MISTask.maxCachedSamples);

        MISTask.maxCachedRecordBytes = MISParams.parseInt(getInitParameter("max_cached_record_bytes"),
                                                          SparseImage.DEF_MAX_CACHED_RECORD_BYTES);
        log.info(pfx + "max cached record bytes: " + MISTask.maxCachedRecordBytes);

        MISTask.histogramBinWidth = MISParams.parseDouble(getInitParameter("surface_histogram_bin_width"),
                                                          MISTask.DEF_HISTOGRAM_BIN_WIDTH);
        log.info(pfx + "surface histogram bin width: " + MISTask.histogramBinWidth);

        MISTask.histogramMaxBins = MISParams.parseInt(getInitParameter("surface_histogram_max_bins"),
                                                      MISTask.DEF_HISTOGRAM_MAX_BINS);
        log.info(pfx + "surface histogram max bins: " + MISTask.histogramMaxBins);

        SparseImage.fsInputDir = getInitParameter("fs_input_dir");
        if (SparseImage.fsInputDir != null) {
            SparseImage.fsInputDir = SparseImage.fsInputDir.trim();
            SparseImage.fsInputDir.replace("\\", "/");
            while (SparseImage.fsInputDir.endsWith("/")) {
                SparseImage.fsInputDir = SparseImage.fsInputDir.substring(0, SparseImage.fsInputDir.length() - 1);
            }
            if (SparseImage.fsInputDir.length() > 0) {
                SparseImage.fsInputDir += "/";
            }
        }
        log.info(pfx + "filesystem input directory: " + SparseImage.fsInputDir); //null OK
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleRequest(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String pfx = logPrefix(request);
        if ("application/json".equals(request.getContentType())) {
            handleRequest(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            log.error(pfx + "bad request, POST must be application/json");
        }
    }

    @Override
    public void destroy() {
        try {
            S3Helper.destroyCache();
        } catch (InterruptedException ex) {
            log.error("interrupted waiting for S3 credential cache cleanup");
        }
    }

    private void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pfx = logPrefix(request);
        String requestPath = request.getPathInfo(); //starts with slash
        MISRequest req = null;
        try {

            //https://stackoverflow.com/a/5563656
            boolean overload = false;
            String msg = "";
            synchronized (requestCountLock) {
                if (maxConcurrentRequests > 0 && concurrentRequests >= maxConcurrentRequests) {
                    overload = true;
                } else {
                    concurrentRequests++;
                }
                msg = ", currently processing " + concurrentRequests + " requests";
            }
            if (overload) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                log.error(pfx + "rejected request, already processing at least " + maxConcurrentRequests + " requests");
                return;
            }

            if (requestPath != null && requestPath.toLowerCase().endsWith("/version")) {

                serveText(MISTask.getVersion(getClass()), response);

            } else {
                
                req = new MISRequest(request, response, pfx);

                //check first to catch http[s]://BUCKET.s3-REGION.amazonaws.com/KEY`
                boolean isS3 = S3Helper.isS3Url(req.params.imgUrl);
                if (isS3 && !enableS3) {
                    throw new IllegalArgumentException("S3 image URLs disabled");
                }

                boolean isHTTP = !isS3 && HTTPHelper.isHTTPUrl(req.params.imgUrl);
                if (isHTTP && !enableHTTP) {
                    throw new IllegalArgumentException("HTTP[S] image URLs disabled");
                }

                if (!isS3 && !isHTTP && (SparseImage.fsInputDir == null || SparseImage.fsInputDir.length() == 0)) {
                    throw new IllegalArgumentException("non-S3 image URLs disabled");
                }
                
                MISTask task = new MISTask(req.params, req.s3);

                log.info(pfx + "serving " + task.getLogMessage() + msg);

                if (task.isStreaming()) {
                    response.setContentType("application/json");
                    task.run(response.getOutputStream());
                } else {
                    serveJSON(task.run(), response);
                }
            }

        } catch (IllegalArgumentException ex) {

            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            log.error(pfx + "bad request: " + ex.getMessage());

        } catch (ClientAbortException ex) {

            log.info(pfx + "connection closed by client");

        } catch (S3Exception ex) {

            int code = ex.statusCode();
            response.setStatus(code >= 400 ? code : HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            log.error(pfx + "S3 error: " + ex.getMessage());
            
        } catch (SdkClientException | IllegalStateException ex) {

            String cpm = null;
            if (ex.getCause() instanceof ConnectionPoolTimeoutException) {
                //might be due to exhaustion of available entries in the S3 client connection pool
                //we are supposed to be closing every S3 connection for sure
                //but just in case, this could help keep the server running
                //also see comments in S3Helper constructor
                cpm = ex.getCause().getMessage();
                if (cpm == null) {
                    cpm = "ConnectionPoolTimeoutException";
                }
            }
            //java.lang.IllegalStateException: Connection pool shut down
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("connection pool")) {
                cpm = ex.getMessage();
            }
            if (ex.getCause() != null && ex.getCause().getMessage() != null &&
                ex.getCause().getMessage().toLowerCase().contains("connection pool")) {
                cpm = ex.getCause().getMessage();
            }
            if (cpm != null && enableS3) {
                log.warn(pfx + cpm + ", replacing S3 client");
                try {
                    //depending on the internal implementation of the AWS S3 client this could still be a resource leak
                    //because the old S3 client's stale http connections might still be consuming resources
                    //this can also happen in low memory situations because apache thinks all they can do is punt
                    //https://github.com/apache/httpcomponents-client/commit/ca98ad69adad79de57d8b944ba524f7267a795cb
                    //https://github.com/aws/aws-sdk-java/issues/2337
                    initS3(pfx);
                } catch (Exception ex2) {
                    //already logged
                }
            }
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            log.error(pfx + "error handling request", ex);

        } catch (IOException ex) {

            while (ex.getCause() instanceof IOException) {
                ex = (IOException)(ex.getCause());
            }
            if (ex instanceof FileNotFoundException || ex instanceof NoSuchFileException) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                log.error(pfx + "file not found: " + ex.getMessage());
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                log.error(pfx + "I/O error handling request", ex);
            }

        } catch (Exception ex) {

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            log.error(pfx + "error handling request", ex);

        } catch (LinkageError ex) {

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            log.error(pfx + "error handling request: linkage error" + ex.getMessage(), ex);

        } finally {
            if (req != null && req.s3 != null && req.s3 != s3) {
                req.s3.close();
            }
            synchronized (requestCountLock) {
                concurrentRequests--;
                if (concurrentRequests < 0) {
                    concurrentRequests = 0;
                }
            }
        }
    }

    private void initS3(String pfx) {

        int s3CacheSize = MISParams.parseInt(getInitParameter("cache_s3_credentials"), S3Helper.DEF_CACHE_SIZE);
        log.info(pfx + "S3 credential cache size: " + s3CacheSize);
        
        long s3CacheMS =
            1000L * MISParams.parseInt(getInitParameter("max_s3_credentials_age"), S3Helper.DEF_MAX_CACHE_AGE_SEC);
        log.info(pfx + "S3 credential cache timeout: " + (s3CacheMS / 1000.0) + "s");

        S3Helper.setupCache(s3CacheSize, s3CacheMS, log, pfx, MISTask.debug);

        if (useAWSHeader != S3Helper.UseAWSHeader.always) {
            try {
                s3 = new S3Helper(awsProfile, awsRegion); //null ok
                log.info(pfx + "S3 AWS profile: " + s3.getAWSProfile() + ", region: " + s3.getAWSRegion());
            } catch (Exception ex) {
                log.error(pfx + "error connecting to Amazon S3", ex);
                throw ex;
            }
        }
    }

    private String logPrefix(HttpServletRequest request) {
        String requestPath = request.getPathInfo(); //starts with slash, but may be null
        String requestQuery = request.getQueryString(); //may be null
        return MISTask.getVersion() + " [" +
            (requestPath != null ? requestPath : "") +
            (requestQuery != null ? ((requestPath != null ? "?" : "") + requestQuery) : "") + "] ";
    }

    private void serveJSON(String json, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        try (OutputStream out = response.getOutputStream()) {
            out.write(json.getBytes("UTF-8"));
        }
    }

    private void serveText(String text, HttpServletResponse response) throws IOException {
        response.setContentType("text/plain");
        try (OutputStream out = response.getOutputStream()) {
            out.write(text.getBytes("UTF-8"));
        }
    }

    private class MISRequest {
        
        public final HttpServletRequest request;
        public final HttpServletResponse response;
        
        public final MISParams params;

        public final S3Helper s3;

        public MISRequest(HttpServletRequest request, HttpServletResponse response, String pfx)
            throws IOException {

            this.request = request;
            this.response = response;

            params = MISParams.parseRequest(request, mission);

            if (enableS3) {
                S3Helper reqS3 = S3Helper.getRequestS3(request, useAWSHeader, MISTask.debug ? log : null, pfx);
                s3 = reqS3 != null ? reqS3 : MISService.this.s3;
            } else {
                s3 = null;
            }
        }
    }
}
