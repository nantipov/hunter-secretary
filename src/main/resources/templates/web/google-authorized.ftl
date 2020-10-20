<html>
    <head>
        <title>GmailAuth</title>
    </head>
    <body>
    <#if error!""?length == 0>
        <p style="color: green;">DONE</p>
    <#else>
        <p style="color: darkred;">ERROR: ${error!""}</p>
    </#if>
<#--        <p>${code!""}</p>-->
    </body>
</html>
