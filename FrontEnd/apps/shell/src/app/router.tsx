import { createBrowserRouter } from 'react-router'
import HomePage from '../pages/home/HomePage'
import LoginPage from '../pages/auth/LoginPage'

export const router = createBrowserRouter([
    {
        path: '/',
        element: <HomePage />,
    },
    {
        path: '/login',
        element: <LoginPage />,
    },
])