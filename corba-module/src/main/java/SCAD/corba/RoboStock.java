package SCAD.corba;

import SCAD.*;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;

public class RoboStock {
    public static void main(String[] args) {
        try {
            ORB orb = ORB.init(args, null);
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            Auditoria auditor = AuditoriaHelper.narrow(ncRef.resolve_str("SCAD.Auditoria"));
            System.out.println("✅ [ROBÔ] Ligado ao Auditor!");

            System.out.println("[ROBÔ] Stock de Parafusos: " + auditor.consultarSaldo("Parafusos"));
            auditor.dispararAlertaStock("Parafusos", 5);
            auditor.registarCompra("Parafusos", 100, "Fornecedor ABC");

            System.out.println("[ROBÔ] Operações concluídas.");
        } catch (Exception e) {
            System.err.println("Erro no robô: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
