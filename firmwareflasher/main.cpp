#include <ftxui/component/screen_interactive.hpp>
#include <ftxui/component/component.hpp>
#include <ftxui/component/component_base.hpp>
#include <ftxui/dom/elements.hpp>

#include <cstdlib>
#include <string>
#include <vector>
#include <sstream>

#include <limits.h>
#include <mach-o/dyld.h>
#include <libgen.h>
#include <unistd.h>
#include <stdlib.h>

using namespace ftxui;

ButtonOption CenteredButtonOption() {
    ButtonOption option;
    option.transform = [](const EntryState& s) {
        auto content = hbox(filler(), text(s.label), filler());
        if (s.active)  content |= bold;
        if (s.focused) content |= inverted;
        return content;
    };
    return option;
}

std::string GetExecutableDir() {
    char path[PATH_MAX];
    uint32_t size = sizeof(path);

    if (_NSGetExecutablePath(path, &size) != 0) {
        return ".";
    }

    char resolved[PATH_MAX];
    if (realpath(path, resolved) == nullptr) {
        // If realpath fails, fall back to the non-resolved path
        return std::string(dirname(path));
    }

    return std::string(dirname(resolved));
}

int main() {
    auto screen = ScreenInteractive::Fullscreen();

    bool setupbutton = false;
    bool board = false;

    std::vector<std::string> ls_lines;

    auto entersetupbutton = Button(
        "Enter Setup",
        [&] {
            setupbutton = true;

            std::string exe_dir = GetExecutableDir();

            std::string cmd =
                "osascript "
                "-e 'tell application \"Terminal\" to activate' "
                "-e 'tell application \"Terminal\" to do script "
                "\"cd \\\"" + exe_dir + "\\\" && "
                "mkdir -p testclone && "
                "git clone https://github.com/ArthurSonzogni/FTXUI.git testclone/FTXUI; "
                "echo; echo DONE; echo Press any key to close...; read -n 1\"'";

            std::system(cmd.c_str());
        },
        CenteredButtonOption()
    );

    auto selectboardbutton = Button(
        "Select Board",
        [&] { board = true; },
        CenteredButtonOption()
    );

    auto buttons = Container::Vertical({
        entersetupbutton,
        selectboardbutton,
    });

    auto renderer = Renderer(buttons, [&] {
        return vbox({
            text("███████╗███████╗██████╗     ███████╗██╗      █████╗ ███████╗██╗  ██╗███████╗██████╗ ")| center,
            text("██╔════╝██╔════╝██╔══██╗    ██╔════╝██║     ██╔══██╗██╔════╝██║  ██║██╔════╝██╔══██╗")| center,
            text("█████╗  ███████╗██████╔╝    █████╗  ██║     ███████║███████╗███████║█████╗  ██████╔╝")| center,
            text("██╔══╝  ╚════██║██╔═══╝     ██╔══╝  ██║     ██╔══██║╚════██║██╔══██║██╔══╝  ██╔══██╗")| center,
            text("███████╗███████║██║         ██║     ███████╗██║  ██║███████║██║  ██║███████╗██║  ██║")| center,
            text("╚══════╝╚══════╝╚═╝         ╚═╝     ╚══════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝")| center,
            separator(),
            filler(),
            
            hbox({ filler(), text("Welcome to the setup window") | center, filler() }),
            hbox({ filler(), text("Follow the steps in the program or visit blah blah blah") | center, filler() }),
            text("") | center,

            hbox({ filler(), entersetupbutton->Render(), filler() }),
            hbox({ filler(), selectboardbutton->Render(), filler() }),

            filler(),

        }) | flex | border;
    });

    screen.Loop(renderer);
}
