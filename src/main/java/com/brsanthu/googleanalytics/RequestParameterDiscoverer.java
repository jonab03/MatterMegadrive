package com.brsanthu.googleanalytics;

/**
 * Mechanism to discover some default request parameters.
 */
public interface RequestParameterDiscoverer {

    DefaultRequest discoverParameters(GoogleAnalyticsConfig config, DefaultRequest request);

}
