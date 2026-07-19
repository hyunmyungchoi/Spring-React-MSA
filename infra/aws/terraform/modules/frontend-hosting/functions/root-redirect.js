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

    return request;
}
