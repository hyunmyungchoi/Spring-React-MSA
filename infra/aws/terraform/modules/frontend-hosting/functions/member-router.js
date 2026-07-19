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

function querySuffix(querystring) {
    var pairs = [];
    var keys = Object.keys(querystring).sort();

    for (var i = 0; i < keys.length; i++) {
        var key = keys[i];
        var parameter = querystring[key];
        var values = parameter.multiValue || [parameter];

        for (var j = 0; j < values.length; j++) {
            pairs.push(encodeURIComponent(key) + '=' + encodeURIComponent(values[j].value));
        }
    }

    return pairs.length === 0 ? '' : '?' + pairs.join('&');
}

function handler(event) {
    var request = event.request;
    if (request.headers.host && request.headers.host.value === 'hyuncloudlab.com') {
        return {
            statusCode: 308,
            statusDescription: 'Permanent Redirect',
            headers: {
                location: { value: 'https://app.hyuncloudlab.com' + request.uri + querySuffix(request.querystring) }
            }
        };
    }

    var routed = scopedRoute(request, '/community', 'community.html');
    if (routed !== null) {
        return routed;
    }

    routed = scopedRoute(request, '/stock', 'stock.html');
    if (routed !== null) {
        return routed;
    }

    if (request.uri.endsWith('/') || !hasFileExtension(request.uri)) {
        request.uri = '/index.html';
    }
    return request;
}
