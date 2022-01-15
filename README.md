# BHBot, ima18h's version
The bot was almost not working when i cloned it because of new game updates, but it only needed some refreshing. 
I mostly added functionality and also maintained it so that it would work with updated versions of the game. 
The bot is deprecated and doesn't work anymore. The original authors' repository is also archived.  

At the time, I was not using git. 
After all this time, I thought it would be cool to put this in a repo. 
To do this I forked the original repo by ilpersi/Betalord and went back to the commit that I had used. 


## Update
From the original repository:
Kongregate switched from Flash to Unity technology making this tool no longer working. Please use another tool.


## What is BHBot?
BHBot is a program that automates different aspects of the [Bit Heroes](http://www.kongregate.com/games/juppiomenz/bit-heroes) game.
It is a non intrusive program that works by opening up a Chrome window (or a Firefox, or any other browser for which Selenium WebDriver
driver exists) and controls it as a normal user would. It works by taking screenshot every 500 ms, detects cues from screenshot and
then simulates mouse clicks. BHBot is a good example of how to create such bots to automate flash games or other browser games
(it can be easily adjusted to play some other game).


## First time use
1) Before actually running the bot, you need to run a [ChromeDriver](https://sites.google.com/a/chromium.org/chromedriver/) instance.
You need to download correct version (32/64 bit), or else it may not work.
2) You will need Java runtime installed on your system in order for the bot to run. You may run it by directly running the jar file,
or by running pre-compiled exe file (Windows only, requires JRE as well).
3) Inspect `settings.ini` file and adjust to your needs.
4) When you run the bot for the first time, it is best that you run it using the `init.bat` file. This will make sure the
bot doesn't perform any operations (it will go into idle mode). It will leave chrome window open for you to set up certain
settings within the game (like familiars filter, notifications filter, etc.). Once you're done setting it, close the chrome
window and the bot's console window. This will create certain cookies within your Chrome profile folder that will be used
the next time you run the bot. Alternativelly to using `init.bat`, you can run bot normally and immediatelly after it loads,
issue command `pause` which will make sure bot leave Chrome window to you.
5) As described in the previous step, you should turn off notifications in the game's settings, since that obscures the energy
and PvP ticket bars and may interfere with with energy/tickets detection routine.
6) When you first run the bot, a 'chrome_profile' folder will be created by the chrome driver, where your chrome profile will
be saved (cookies, etc.). You should not touch that folder.


## Commands
Here is a list of most common commands used with the bot (must be typed in the console window, or, if you use web interface, in the
command input box):

- `stop`: stops the bot execution (may take a few seconds. Once it is stopped, the console will close automatically).
- `pause`: pauses bot's execution. Useful when you want to play the game yourself (either from the same Chrome window, or by starting another Chrome window).
- `resume`: resumes bot's execution.
- `reload`: will reload the 'settings.ini' file from disk and apply any changes on-the-fly.
- `shot`: takes a screenshot of the game and saves it to 'shot.png'.
- `restart`: restarts the Chrome driver (closes Chrome and opens a fresh Chrome window). Use only when something goes wrong (should restart automatically after some time in that case though).
- `hide`: hides Chrome window.
- `show`: shows Chrome window again after it has been hidden.
- `do`: force a dungeon/raid/pvp/gvg/gauntlet/trials. Example: "do raid". Used for debugging purposes more or less (bot will automatically attempt dungeons).
- `set`: sets a setting line, just like from a 'settings.ini' file. Example: "set raids 1 3 100", or "set difficulty 70". Note that this overwritten setting is NOT saved to the 'settings.ini' file! Once you issue <reload> command, it will get discharged.
- `plan`: will load a settings file from a plan folder. Some sample plan files are attached to this distribution. Example: "plan idle" (will load 'plans/idle.ini' and overwritte current settings with it). You should write your own plan files as needed.
- `readouts`: will reset readout timers (and hence immediatelly commence reading out resources).


## Advanced
If you want to run 2 instances of bot in parallel (or even more), then you'll probably need to run two instances of
ChromeDriver as well (at least on Windows). That can be done, but needs some adjustments. First of all, you'll need to run your
chrome driver like this:
`chromedriver.exe --port=9550`
(in case you want your chrome driver to run on port 9550). The next chrome driver instance will run e.g. on port 9551,
so we'll run it like this: `chromedriver.exe --port=9551`.
Now, in order to tell the bot to connect to chrome driver on one of these ports, you'll need to run it like this:
`bhbot.exe chromedriveraddress 127.0.0.1:9550`. This will make sure that the bot will connect to the first chrome driver instance.
The second bot instance should be run like this:
`bhbot.exe chromedriveraddress 127.0.0.1:9551`. This should make two bots run in parallel without disturbing each other.


## An example Linux setup
Here I give an example setup of the bot (and web interface) for Linux. Note that web interface is possible only when BHBot runs
in Linux, since it injects commands to `screen`, with which bot should be ran.

Lets see a typical folder structure:

* `/home/betalord/bhbot` root folder for the BHBot. This is where you put your bhbot.jar file, chromedriver, settings.ini, runbot file
(find it under [web/linux scripts](https://github.com/Betalord/BHBot/tree/master/web/linux%20scripts)), and the rest of the base files.
* `/home/betalord/bhbot/webserver` root folder for web interface. Here you need to put most of the files from
[web/linux scripts](https://github.com/Betalord/BHBot/tree/master/web/linux%20scripts) (all files except for `runbot`).
* `/home/betalord/bhbot/webserver/webroot` here goes contents of
[web/](https://github.com/Betalord/BHBot/tree/master/web/) (except for `linux_scripts` folder).
* `/home/betalord/bhbot/webserver/webroot/srv_bin` here you put the HTTPServer class files (and all the rest of class files that
compiling the HTTPServer produces, like `Misc` and `FletcherChecksum` classes).
* `/home/betalord/bhbot/marvin` here goes the marvin library files.
* `/home/betalord/bhbot/cues` here goes the cues (image files).
* `/home/betalord/bhbot/plans` here goes the plans files.

In order to run BHBot, you need to run it as following (or else web server won't be able to inject commands):

`screen -d -m -S bhbot`

If you need to access bot console, use the following command:

`screen -r bhbot`

You'll need to run chromedriver and HTTPServer (see
[web/linux scripts](https://github.com/Betalord/BHBot/tree/master/web/linux%20scripts) on how to run it). 


## Compiling
The project uses only a fraction of the Marvin framework (does not depend on it - I've copied the required files into the src
folder) for image cue recognition. It heavily uses Selenium WebDriver for interaction with the web browser (jars included). It also
uses Selenium Shutterbug to take screenshots (jars included). 
