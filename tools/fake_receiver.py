"""Local-only protocol simulator. It is not Notch and is not a production server."""
import argparse
from collections import deque
import base64
import hashlib
import json
import os
import pathlib
import socket
import ssl
import struct
import subprocess
import tempfile
import threading
import uuid

MAX_FRAME = 1_048_576
DOMAIN = b"hesewake-media-pair-v1"


def encode(value):
    return base64.urlsafe_b64encode(value).decode().rstrip("=")


def decode(value):
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def code(fingerprint, client, server):
    digest = hashlib.sha256(DOMAIN + fingerprint + client + server).digest()
    return f"{int.from_bytes(digest[:4], 'big') % 1_000_000:06d}"


def send(peer, kind, **fields):
    data = json.dumps({"v": 1, "type": kind, **fields}, separators=(",", ":")).encode()
    if not 0 < len(data) <= MAX_FRAME:
        raise ValueError("invalid frame size")
    peer.sendall(struct.pack(">I", len(data)) + data)


def exact(peer, count):
    data = bytearray()
    while len(data) < count:
        part = peer.recv(count - len(data))
        if not part:
            raise EOFError("closed")
        data.extend(part)
    return bytes(data)


def read(peer):
    size = struct.unpack(">I", exact(peer, 4))[0]
    if not 0 < size <= MAX_FRAME:
        raise ValueError("invalid frame size")
    msg = json.loads(exact(peer, size))
    if msg.get("v") != 1 or not isinstance(msg.get("type"), str):
        raise ValueError("invalid frame")
    return msg


def make_identity(folder):
    key, cert = os.path.join(folder, "key.pem"), os.path.join(folder, "cert.pem")
    subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1", "-subj", "/CN=Hesewake Test", "-keyout", key, "-out", cert], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    with open(cert, encoding="ascii") as f:
        der = ssl.PEM_cert_to_DER_cert(f.read())
    return key, cert, hashlib.sha256(der).digest()


def server(listener, context, fingerprint, count, interactive, observations, command=None):
    token = None
    for _ in range(count):
        raw, _address = listener.accept()
        with context.wrap_socket(raw, server_side=True) as peer:
            peer.settimeout(15 if not interactive else 120)
            first = read(peer)
            if first["type"] == "pair_hello":
                client = decode(first["client_nonce"])
                if len(client) != 32:
                    raise ValueError("invalid client nonce")
                nonce = os.urandom(32)
                send(peer, "pair_challenge", server_nonce=encode(nonce))
                pairing_code = code(fingerprint, client, nonce)
                if interactive:
                    print("Cocokkan kode di HP:", pairing_code, flush=True)
                decision = read(peer)
                if decision.get("type") != "pair_decision":
                    raise ValueError("missing decision")
                accept = decision.get("approved") is True and (not interactive or input("Kode sama? [y/N] ").strip().lower() == "y")
                send(peer, "pair_decision", approved=accept)
                if not accept:
                    continue
                token = os.urandom(32)
                send(peer, "pair_result", approved=True, token=encode(token))
                observations.append(("paired", pairing_code))
            elif first["type"] == "auth":
                accepted = token is not None and __import__("hmac").compare_digest(decode(first["token"]), token)
                send(peer, "auth_result", approved=accepted)
                if not accepted:
                    continue
                observations.append(("auth",))
            else:
                raise ValueError("unauthenticated message")
            if command is not None:
                ident = str(uuid.uuid4())
                fields = {"id": ident, "action": command[0]}
                if command[0] == "seek":
                    fields["position_ms"] = command[1]
                send(peer, "command", **fields)
                if interactive:
                    print("Perintah uji dikirim:", command[0], flush=True)
            try:
                while True:
                    msg = read(peer)
                    if msg["type"] == "ping":
                        send(peer, "pong", id=msg["id"])
                    elif msg["type"] == "snapshot":
                        observations.append(("snapshot", msg.get("seq")))
                    elif msg["type"] == "ack":
                        observations.append(("ack", msg.get("id"), msg.get("accepted")))
                    else:
                        raise ValueError("unexpected frame")
            except (EOFError, ConnectionResetError, ssl.SSLError, socket.timeout):
                pass


def self_test(listener, fingerprint):
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    token = None
    client_code = None
    for index in range(4):
        with ctx.wrap_socket(socket.create_connection(listener.getsockname()), server_hostname="localhost") as peer:
            assert hashlib.sha256(peer.getpeercert(binary_form=True)).digest() == fingerprint
            if index in (0, 1):
                client = bytes(range(32))
                send(peer, "pair_hello", client_nonce=encode(client))
                challenge = read(peer)
                client_code = code(fingerprint, client, decode(challenge["server_nonce"]))
                send(peer, "pair_decision", approved=index == 1)
                assert read(peer)["approved"] is (index == 1)
                if index == 0:
                    continue
                token = decode(read(peer)["token"])
                assert len(token) == 32 and len(client_code) == 6
            else:
                send(peer, "auth", token=encode(bytes(32) if index == 2 else token))
                assert read(peer)["approved"] is (index == 3)
                if index == 2:
                    continue
            command = read(peer)
            assert command["type"] == "command" and command["action"] == "play"
            send(peer, "ack", id=command["id"], accepted=True)
            send(peer, "snapshot", seq=index, active=False)
            ident = str(uuid.uuid4())
            send(peer, "ping", id=ident)
            assert read(peer) == {"v": 1, "type": "pong", "id": ident}
    return client_code


def dns_name(name):
    labels = name.rstrip(".").split(".")
    return b"".join(bytes([len(label.encode())]) + label.encode() for label in labels) + b"\0"


def record(name, kind, payload):
    return dns_name(name) + struct.pack(">HHIH", kind, 1, 120, len(payload)) + payload


def advertise(address, port, stop):
    service = "_hesewake-media._tcp.local"
    instance = "Hesewake Test." + service
    host = "hesewake-test.local"
    packet = struct.pack(">HHHHHH", 0, 0x8400, 0, 4, 0, 0)
    packet += record(service, 12, dns_name(instance))
    packet += record(instance, 33, struct.pack(">HHH", 0, 0, port) + dns_name(host))
    packet += record(instance, 16, b"\x03v=1")
    packet += record(host, 1, socket.inet_aton(address))
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP) as udp:
        udp.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 255)
        udp.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_IF, socket.inet_aton(address))
        while not stop.is_set():
            udp.sendto(packet, ("224.0.0.251", 5353))
            stop.wait(3)


def write_qr(payload, jar, destination, folder):
    source = str(pathlib.Path(__file__).with_name("QrImage.java"))
    subprocess.run(["javac", "-cp", jar, "-d", folder, source], check=True)
    subprocess.run(["java", "-cp", folder + os.pathsep + jar, "QrImage", payload, destination], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--serve", action="store_true", help="Run the test receiver on a LAN interface")
    parser.add_argument("--address", help="IPv4 address of this machine for discovery and QR")
    parser.add_argument("--zxing-jar", help="Path to ZXing core jar, for a local PNG QR")
    parser.add_argument("--qr-output", default="receiver-qr.png")
    parser.add_argument("--command", choices=["play", "pause", "next", "previous", "seek"], help="Send one test command after each successful connection")
    parser.add_argument("--position-ms", type=int, default=0, help="Seek position for --command seek")
    args = parser.parse_args()
    if args.self_test == args.serve:
        parser.error("Choose exactly one of --self-test or --serve")
    if args.serve and not args.address:
        parser.error("--serve requires --address with a local IPv4 address")
    if args.position_ms < 0:
        parser.error("--position-ms must be non-negative")
    with tempfile.TemporaryDirectory(prefix="hesewake-receiver-") as folder:
        key, cert, fingerprint = make_identity(folder)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(cert, key)
        with socket.socket() as listener:
            listener.bind((args.address if args.serve else "127.0.0.1", 0)); listener.listen(4)
            if args.serve:
                port = listener.getsockname()[1]
                invitation = {"host": args.address, "port": port, "fingerprint": fingerprint.hex()}
                payload = "hesewake-media:1:" + encode(json.dumps(invitation, separators=(",", ":")).encode())
                print("Test receiver:", args.address, port, flush=True)
                print("QR payload:", payload, flush=True)
                if args.zxing_jar:
                    write_qr(payload, args.zxing_jar, args.qr_output, folder)
                    print("QR image:", os.path.abspath(args.qr_output), flush=True)
                stop = threading.Event()
                thread = threading.Thread(target=advertise, args=(args.address, port, stop), daemon=True); thread.start()
                try:
                    command = (args.command, args.position_ms) if args.command else None
                    server(listener, context, fingerprint, 1000000, True, deque(maxlen=100), command)
                finally:
                    stop.set(); thread.join(5)
                return
            listener.settimeout(15)
            observations, errors = [], []
            def run_server():
                try:
                    server(listener, context, fingerprint, 4, False, observations, ("play", None))
                except Exception as error:
                    errors.append(error)
            thread = threading.Thread(target=run_server, daemon=True); thread.start()
            client_code = self_test(listener, fingerprint)
            thread.join(15)
            if thread.is_alive() or errors:
                raise RuntimeError(f"receiver failed: {errors}")
            assert observations[0] == ("paired", client_code) and ("auth",) in observations
            assert ("snapshot", 1) in observations and ("snapshot", 3) in observations
            assert sum(1 for entry in observations if entry[0] == "ack" and entry[2] is True) == 2
            print("PASS: pairing rejection, matching code, rejected token, reconnect, commands/acks, snapshots and ping/pong")


if __name__ == "__main__":
    main()
