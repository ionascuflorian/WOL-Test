import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Wake on LAN",
  description: "Controlează pornirea laptopului de la distanță",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ro" className="h-full antialiased">
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `(function(){try{var t=localStorage.getItem("wol-theme");var d=t==="dark"||(t!==null&&t==="light"?false:window.matchMedia("(prefers-color-scheme: dark)").matches);if(d)document.documentElement.classList.add("dark")}catch(e){}})();`,
          }}
        />
      </head>
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}