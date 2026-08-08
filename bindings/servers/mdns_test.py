import socket
import time
from zeroconf import ServiceInfo, Zeroconf

def advertise_service():
    # 1. Gather local network details
    # hostname = socket.gethostname()
    hostname = input("Enter host name: ")
    port = int(input("Enter port: "))
    local_ip = socket.gethostbyname(hostname)

    # 2. Define the service details
    service_type = "_fserver._tcp.local."
    service_name = f"MyPythonWebServer.{service_type}"

    info = ServiceInfo(
        type_=service_type,
        name=service_name,
        addresses=[socket.inet_aton(local_ip)],
        port=port,
        properties={"version": "1.0", "path": "/index.html"},
        server=f"{hostname}.local.",
    )

    # 3. Register and broadcast the service
    zeroconf = Zeroconf()
    print(f"Broadcasting service '{service_name}' on {local_ip}:{port}...")
    zeroconf.register_service(info)

    try:
        while True:
            time.sleep(0.1)
    except KeyboardInterrupt:
        pass
    finally:
        print("Unregistering service...")
        zeroconf.unregister_service(info)
        zeroconf.close()

if __name__ == "__main__":
    advertise_service()
