package net.tmclean.pxeserver.rest.util;

import javax.servlet.http.HttpServletRequest;

import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerMapping;

public final class PathUtils {
	
	public static String extractRestOfWildcardPath( HttpServletRequest request ) {

	    final String path = request.getAttribute( HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE ).toString();
	    final String bestMatchingPattern = request.getAttribute( HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE ).toString();

	    return new AntPathMatcher().extractPathWithinPattern( bestMatchingPattern, path );
	    
	}
	
	private PathUtils() {}
}
