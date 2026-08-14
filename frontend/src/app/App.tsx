import { RouterProvider } from "react-router-dom";
import { AuthProvider } from "../auth/AuthContext";
import { SignupDraftProvider } from "../auth/SignupDraftContext";
import { ToastProvider } from "../components/common/Toast";
import { router } from "./router";

export function App() {
  return (
    <AuthProvider>
      <SignupDraftProvider>
        <ToastProvider>
          <RouterProvider router={router} />
        </ToastProvider>
      </SignupDraftProvider>
    </AuthProvider>
  );
}
