
import com.google.common.net.InternetDomainName;

public class TestGuava {
    public static void main(String[] args) {
        System.out.println("nodot: " + InternetDomainName.isValid("nodot"));
        System.out.println("trailing.: " + InternetDomainName.isValid("trailing."));
        System.out.println("example.com: " + InternetDomainName.isValid("example.com"));
    }
}
