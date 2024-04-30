# Review instructions for Google Play

Normally, the app only supports strong identification through Suomi.fi, meaning
that users normally log in using their bank credentials (or some other form of
strong identification).

For review purposes, the app has a "test login" page, which can be accessed
only through a deep link.

Please follow these instructions to review the app:
1. make sure the app is closed (not running in the background)
2. then open this deep link on the test device:
   - ekirjasto://test-login
3. this opens a test login page (accessible only with the above deep link)
4. input the test login credentials given in Google Play Console
5. after pressing "Login" on the test login page, the app will reload and show
   the app's normal login page
6. on the normal login page, choose "Sign in with Suomi.fi"
7. on the next page ("e-Identification"), scroll down and choose "Test IdP"
8. on the next page ("Tunnistus"), above the "Henkilötunnus" input box, click
   the link labeled "Käytä oletusta 210281-9988" and then click "Tunnistaudu"
9. on the next page, click "Continue to service"

After the above steps, you are logged in like regular users.
