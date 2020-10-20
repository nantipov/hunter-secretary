package org.nantipov.huntersecretary.controllers.web;

import org.nantipov.huntersecretary.services.Utils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class Welcome {

    @RequestMapping("/")
    public String home(Model model) {
        return Utils.templateWeb(Utils.TEMPLATE_WEB_HOME, model);
    }

    @RequestMapping("/policy")
    public String policy(Model model) {
        return Utils.templateWeb(Utils.TEMPLATE_WEB_POLICY, model);
    }
}
