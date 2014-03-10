$(document).ready(function(){
    $('#sidebar').affix({
        offset: {
          top: 200
        }
    });                

    var $body   = $(document.body);
    //var navHeight = $('.navbar').outerHeight(true) + 10;

    $body.scrollspy({
        target: '#nav-column',
        offset: 100
    });                    
})