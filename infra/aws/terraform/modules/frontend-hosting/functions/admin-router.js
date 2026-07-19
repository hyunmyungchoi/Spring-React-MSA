function hasFileExtension(uri) {
    var lastSlash = uri.lastIndexOf('/');
    var lastDot = uri.lastIndexOf('.');
    return lastDot > lastSlash;
}

function scopedRoute(request, prefix, document) {
    var uri = request.uri;

    if (uri === prefix) {
        return {
            statusCode: 308,
            statusDescription: 'Permanent Redirect',
            headers: {
                location: { value: prefix + '/' }
            }
        };
    }

    if (uri.indexOf(prefix + '/') !== 0) {
        return null;
    }

    var relativeUri = uri.substring(prefix.length);
    request.uri = relativeUri.endsWith('/') || !hasFileExtension(relativeUri)
        ? '/' + document
        : relativeUri;
    return request;
}

function handler(event) {
    var request = event.request;
    var routed = scopedRoute(request, '/manage/users', 'users.html');
    if (routed !== null) {
        return routed;
    }

    routed = scopedRoute(request, '/manage/logs', 'logs.html');
    if (routed !== null) {
        return routed;
    }

    if (request.uri.endsWith('/') || !hasFileExtension(request.uri)) {
        request.uri = '/index.html';
    }
    return request;
}
