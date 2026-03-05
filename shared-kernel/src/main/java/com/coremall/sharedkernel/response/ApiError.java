package com.coremall.sharedkernel.response;

import java.util.List;

public record ApiError(String code, String message, List<String> details) {
}
