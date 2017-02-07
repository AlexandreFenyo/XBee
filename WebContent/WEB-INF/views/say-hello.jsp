<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Hello</title>
</head>
<body>

<a HREF="checkAT">Check AT command mode and reset module</a><BR/>
<br/>
<a HREF="checkAT2">Check API command mode</a><BR/>
<br/>
<a HREF="checkAT3">Check remote AT command</a><BR/>
<br/>
<a HREF="checkAT4">Check remote AT command to module Volets</a><BR/>
<br/>
<a HREF="checkAT5">Check module "remote command" ATD44 (led off)</a><BR/>
<a HREF="checkAT6">Check module "remote command" ATD45 (led on)</a><BR/>
<a HREF="checkAT7">Check module "remote command" ATD0 ATD1 ATD2 ATD3 ATD8</a><BR/>

<br/>
<a HREF="sendFiles">send File(s)</a><BR/>

<br/>
<a HREF="shutdown">shutdown w7</a>
<br/>
<a HREF="reboot">reboot w7</a>
<br/>
<a HREF="restartvnc">restart vnc server</a>

<HR/>

Status: <PRE>${statusString}</PRE>


</body>
</html>
