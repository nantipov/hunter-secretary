package org.nantipov.huntersecretary.controllers.web;

import com.google.common.base.Strings;
import org.nantipov.huntersecretary.services.GoogleAuthService;
import org.nantipov.huntersecretary.services.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GoogleAuth {

    private final GoogleAuthService googleAuthService;

    @Autowired
    public GoogleAuth(GoogleAuthService googleAuthService) {
        this.googleAuthService = googleAuthService;
    }

    @RequestMapping("/google-auth")
    public String auth() {
        return "redirect:" + googleAuthService.getGoogleAuthURL();
    }

    @RequestMapping("/google-authorized")
    public String authorized(@RequestParam(value = "error", required = false) String error,
                             @RequestParam(value = "code", required = false) String code,
                             Model model) {
        model.addAttribute("error", error);
        //        model.addAttribute("code", code);
        if (Strings.isNullOrEmpty(error)) {
            googleAuthService.applyAuthCode(code);
        }
        return Utils.templateWeb(Utils.TEMPLATE_WEB_AUTHORIZED, model);
    }
}
