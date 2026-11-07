package com.rpg;

import com.rpg.view.MenuPrincipalUI;

/** Punto de entrada: unicamente arranca la vista principal (MVC estricto). */
public class Main {

    public static void main(String[] args) {
        new MenuPrincipalUI().iniciar();
    }
}
