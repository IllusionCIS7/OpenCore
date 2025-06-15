import Link from "next/link";

export default function Nav() {
  return (
    <nav className="p-4 flex gap-4 border-b mb-4">
      <Link href="/" className="font-bold">
        OpenCore
      </Link>
      <Link href="/suggest">Vorschlagen</Link>
      <Link href="/vote">Abstimmen</Link>
      <Link href="/history">Historie</Link>
      <Link href="/webadmin">Admin</Link>
    </nav>
  );
}
