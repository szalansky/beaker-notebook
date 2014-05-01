$(document).ready(function() {
    $('#sidebar').affix({
        offset: {
            top: 200
        }
    });

    var $body = $(document.body);

    $body.scrollspy({
        target: '#nav-column',
        offset: 100
    });
})